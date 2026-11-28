package com.wanyue.cloud.modules.drive.preview;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Inspects complete ISO BMFF boxes in a bounded prefix; never scans media payload. */
final class VideoContainerInspector {
    private VideoContainerInspector() {}

    static boolean fragmented(byte[] prefix) {
        return boxes(prefix, 0, prefix.length, false);
    }

    private record Box(int offset, int header, int end, String type) {}

    private static java.util.List<Box> children(byte[] data, int start, int end) {
        var result = new java.util.ArrayList<Box>();
        for (int pos = start; pos <= end - 8;) {
            long length = Integer.toUnsignedLong(ByteBuffer.wrap(data, pos, 4).getInt());
            int header = 8;
            if (length == 1) {
                if (pos > end - 16) break;
                length = ByteBuffer.wrap(data, pos + 8, 8).getLong(); header = 16;
            }
            if (length == 0) length = end - pos;
            if (length < header || length > end - pos) break;
            result.add(new Box(pos, header, pos + (int) length, new String(data, pos + 4, 4, StandardCharsets.US_ASCII)));
            pos += (int) length;
        }
        return result;
    }

    private static Box child(byte[] data, Box parent, String type) {
        if (parent == null) return null;
        return children(data, parent.offset + parent.header, parent.end).stream().filter(b -> b.type.equals(type)).findFirst().orElse(null);
    }

    /** Codec configuration from a complete moov avoids probing/decoding remote packets just to start a remux. */
    static PreviewModels.VideoProbe mp4Probe(byte[] data) {
        try {
            Box moov = children(data, 0, data.length).stream().filter(b -> b.type.equals("moov")).findFirst().orElse(null);
            if (moov == null) return null;
            double duration = 0;
            Box mvhd = child(data, moov, "mvhd");
            if (mvhd != null) {
                int body = mvhd.offset + mvhd.header;
                boolean wide = data[body] == 1;
                int timeOffset = body + (wide ? 20 : 12);
                long scale = Integer.toUnsignedLong(ByteBuffer.wrap(data, timeOffset, 4).getInt());
                long ticks = wide ? ByteBuffer.wrap(data, timeOffset + 4, 8).getLong()
                        : Integer.toUnsignedLong(ByteBuffer.wrap(data, timeOffset + 4, 4).getInt());
                if (scale > 0 && ticks > 0) duration = (double) ticks / scale;
            }
            String video = "", audio = "";
            int width = 0, height = 0;
            boolean encodeVideo = true;
            for (Box track : children(data, moov.offset + moov.header, moov.end)) {
                if (!track.type.equals("trak")) continue;
                Box mdia = child(data, track, "mdia");
                Box hdlr = child(data, mdia, "hdlr");
                Box stsd = child(data, child(data, child(data, mdia, "minf"), "stbl"), "stsd");
                if (hdlr == null || stsd == null) continue;
                String handler = new String(data, hdlr.offset + hdlr.header + 8, 4, StandardCharsets.US_ASCII);
                var entries = children(data, stsd.offset + stsd.header + 8, stsd.end);
                if (entries.isEmpty()) continue;
                Box sample = entries.get(0);
                if (handler.equals("vide") && video.isEmpty()) {
                    width = Short.toUnsignedInt(ByteBuffer.wrap(data, sample.offset + 32, 2).getShort());
                    height = Short.toUnsignedInt(ByteBuffer.wrap(data, sample.offset + 34, 2).getShort());
                    video = switch (sample.type) {
                        case "avc1", "avc3" -> "h264";
                        case "hvc1", "hev1" -> "hevc";
                        case "av01" -> "av1";
                        case "vp09" -> "vp9";
                        default -> "unknown";
                    };
                    if (video.equals("h264")) {
                        for (Box config : children(data, sample.offset + 86, sample.end)) {
                            if (config.type.equals("avcC") && config.end > config.offset + config.header + 1) {
                                int profile = Byte.toUnsignedInt(data[config.offset + config.header + 1]);
                                encodeVideo = !java.util.Set.of(66, 77, 100).contains(profile);
                            }
                        }
                    }
                    // MPEG-TS does not preserve MP4's display matrix. Burn rotation/mirroring
                    // into the picture instead of producing a sideways HLS preview.
                    Box tkhd = child(data, track, "tkhd");
                    if (tkhd != null) {
                        int body = tkhd.offset + tkhd.header;
                        int matrix = body + (data[body] == 1 ? 52 : 40);
                        if (matrix + 20 <= tkhd.end && (ByteBuffer.wrap(data, matrix, 4).getInt() != 65536
                                || ByteBuffer.wrap(data, matrix + 4, 4).getInt() != 0
                                || ByteBuffer.wrap(data, matrix + 12, 4).getInt() != 0
                                || ByteBuffer.wrap(data, matrix + 16, 4).getInt() != 65536)) encodeVideo = true;
                    }
                } else if (handler.equals("soun") && audio.isEmpty()) {
                    audio = switch (sample.type) {
                        case "mp4a" -> "aac";
                        case "ac-3" -> "ac3";
                        case "ec-3" -> "eac3";
                        case "Opus" -> "opus";
                        default -> "unknown";
                    };
                }
            }
            if (video.isEmpty() || width <= 0 || height <= 0) return null;
            return new PreviewModels.VideoProbe("mp4", video, audio, duration, width, height, 0,
                    encodeVideo ? "TRANSCODE" : "REMUX", "视频流准备中", "READY", encodeVideo);
        } catch (RuntimeException invalid) { return null; }
    }

    private static boolean boxes(byte[] bytes, int start, int end, boolean inMoov) {
        for (int offset = start; offset <= end - 8;) {
            long length = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset, 4).getInt());
            String type = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
            int header = 8;
            if (length == 1) {
                if (offset > end - 16) return false;
                length = ByteBuffer.wrap(bytes, offset + 8, 8).getLong();
                header = 16;
            }
            if (length == 0) length = end - offset;
            if (length < header) return false;
            if ("moof".equals(type) || (inMoov && "mvex".equals(type))) return true;
            if ("moov".equals(type) && boxes(bytes, offset + header,
                    (int) Math.min(end, offset + length), true)) return true;
            if (length > end - offset) return false;
            offset += (int) length;
        }
        return false;
    }
}
