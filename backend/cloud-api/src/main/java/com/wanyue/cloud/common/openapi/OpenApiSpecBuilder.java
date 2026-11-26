package com.wanyue.cloud.common.openapi;

import com.wanyue.cloud.common.result.R;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/**
 * 从当前 Spring MVC RequestMappingHandlerMapping 自动生成标准 OpenAPI 3.0.3 契约文档。
 * 遵循 T007 规范：统一 R<T> 包装，雪花 ID 传输为 string，时间字段为 ISO 8601 date-time。
 */
@Component
public class OpenApiSpecBuilder {

    private final RequestMappingHandlerMapping handlerMapping;

    public OpenApiSpecBuilder(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    public Map<String, Object> build() {
        Map<String, Map<String, Object>> schemas = new TreeMap<>();
        registerCommonSchemas(schemas);

        Map<String, Map<String, Object>> paths = new TreeMap<>();
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod method = entry.getValue();
            if (method.getBeanType().getPackageName().startsWith("org.springframework")) {
                continue;
            }
            if (method.getBeanType().equals(OpenApiController.class)) {
                continue;
            }
            Set<String> patterns = info.getPatternValues();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            if (patterns.isEmpty() || methods.isEmpty()) {
                continue;
            }
            for (String pattern : patterns) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                Map<String, Object> pathItem = paths.computeIfAbsent(pattern, ignored -> new LinkedHashMap<>());
                for (RequestMethod httpMethod : methods) {
                    pathItem.put(httpMethod.name().toLowerCase(Locale.ROOT), buildOperation(method, httpMethod, schemas));
                }
            }
        }

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", Map.of(
                "title", "Cloudlane API",
                "version", "1.0.0",
                "description", "Cloudlane 个人云端网盘 REST API 契约（自动由 Spring MVC 控制器导出）"
        ));
        spec.put("paths", paths);
        spec.put("components", Map.of("schemas", schemas));
        return spec;
    }

    private void registerCommonSchemas(Map<String, Map<String, Object>> schemas) {
        schemas.put("ApiResponse", Map.of(
                "type", "object",
                "required", List.of("code", "msg"),
                "properties", Map.of(
                        "code", Map.of("type", "integer", "format", "int32"),
                        "msg", Map.of("type", "string"),
                        "data", Map.of("nullable", true),
                        "traceId", Map.of("type", "string", "nullable", true)
                )
        ));
    }

    private Map<String, Object> buildOperation(
            HandlerMethod handlerMethod,
            RequestMethod httpMethod,
            Map<String, Map<String, Object>> schemas) {
        Map<String, Object> operation = new LinkedHashMap<>();
        String controllerName = handlerMethod.getBeanType().getSimpleName();
        operation.put("tags", List.of(controllerName.replace("Controller", "")));
        operation.put("operationId", controllerName + "_" + handlerMethod.getMethod().getName() + "_" + httpMethod.name().toLowerCase(Locale.ROOT));

        List<Map<String, Object>> parameters = new ArrayList<>();
        Map<String, Object> requestBody = null;

        for (MethodParameter param : handlerMethod.getMethodParameters()) {
            PathVariable pathVar = param.getParameterAnnotation(PathVariable.class);
            if (pathVar != null) {
                String name = !pathVar.value().isBlank() ? pathVar.value()
                        : (!pathVar.name().isBlank() ? pathVar.name() : param.getParameterName());
                parameters.add(Map.of(
                        "name", name != null ? name : "id",
                        "in", "path",
                        "required", true,
                        "schema", schemaForType(ResolvableType.forMethodParameter(param), schemas, true)
                ));
                continue;
            }
            RequestParam reqParam = param.getParameterAnnotation(RequestParam.class);
            if (reqParam != null && !MultipartFile.class.isAssignableFrom(param.getParameterType())) {
                String name = !reqParam.value().isBlank() ? reqParam.value()
                        : (!reqParam.name().isBlank() ? reqParam.name() : param.getParameterName());
                if (name != null) {
                    parameters.add(Map.of(
                            "name", name,
                            "in", "query",
                            "required", reqParam.required() && "\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n".equals(reqParam.defaultValue()),
                            "schema", schemaForType(ResolvableType.forMethodParameter(param), schemas, false)
                    ));
                }
                continue;
            }
            if (param.hasParameterAnnotation(RequestBody.class)) {
                requestBody = Map.of(
                        "required", true,
                        "content", Map.of(
                                "application/json", Map.of(
                                        "schema", schemaForType(ResolvableType.forMethodParameter(param), schemas, false)
                                )
                        )
                );
            }
        }

        if (!parameters.isEmpty()) {
            operation.put("parameters", parameters);
        }
        if (requestBody != null) {
            operation.put("requestBody", requestBody);
        }

        ResolvableType returnType = ResolvableType.forMethodReturnType(handlerMethod.getMethod());
        Map<String, Object> okResponse = buildResponse(returnType, schemas);
        operation.put("responses", Map.of("200", okResponse));
        return operation;
    }

    private Map<String, Object> buildResponse(ResolvableType returnType, Map<String, Map<String, Object>> schemas) {
        Class<?> raw = returnType.resolve();
        if (raw != null && ResponseEntity.class.isAssignableFrom(raw)) {
            ResolvableType bodyType = returnType.getGeneric(0);
            Class<?> bodyRaw = bodyType.resolve();
            if (bodyRaw != null && org.springframework.core.io.Resource.class.isAssignableFrom(bodyRaw)) {
                return Map.of(
                        "description", "Binary stream response",
                        "content", Map.of("application/octet-stream", Map.of("schema", Map.of("type", "string", "format", "binary")))
                );
            }
            returnType = bodyType;
            raw = bodyRaw;
        }

        if (raw != null && R.class.isAssignableFrom(raw)) {
            ResolvableType dataType = returnType.getGeneric(0);
            Map<String, Object> dataSchema = schemaForType(dataType, schemas, false);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", "object");
            envelope.put("required", List.of("code", "msg"));
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("code", Map.of("type", "integer", "format", "int32"));
            props.put("msg", Map.of("type", "string"));
            props.put("data", dataSchema);
            props.put("traceId", Map.of("type", "string", "nullable", true));
            envelope.put("properties", props);
            return Map.of(
                    "description", "OK",
                    "content", Map.of("application/json", Map.of("schema", envelope))
            );
        }

        return Map.of(
                "description", "OK",
                "content", Map.of("application/json", Map.of("schema", schemaForType(returnType, schemas, false)))
        );
    }

    private Map<String, Object> schemaForType(
            ResolvableType type,
            Map<String, Map<String, Object>> schemas,
            boolean pathIdAsString) {
        Class<?> raw = type.resolve();
        if (raw == null || raw == Void.class || raw == void.class) {
            return Map.of("nullable", true);
        }
        if (raw == String.class || CharSequence.class.isAssignableFrom(raw)) {
            return Map.of("type", "string");
        }
        if (raw == Boolean.class || raw == boolean.class) {
            return Map.of("type", "boolean");
        }
        if (raw == Integer.class || raw == int.class || raw == Short.class || raw == short.class) {
            return Map.of("type", "integer", "format", "int32");
        }
        if (raw == Long.class || raw == long.class) {
            return pathIdAsString
                    ? Map.of("type", "string", "description", "64-bit identifier transmitted as string")
                    : Map.of("type", "integer", "format", "int64");
        }
        if (Number.class.isAssignableFrom(raw) || raw == double.class || raw == float.class) {
            return Map.of("type", "number");
        }
        if (TemporalAccessor.class.isAssignableFrom(raw) || Date.class.isAssignableFrom(raw)) {
            return Map.of("type", "string", "format", "date-time");
        }
        if (raw.isEnum()) {
            List<String> values = Arrays.stream(raw.getEnumConstants()).map(Object::toString).toList();
            return Map.of("type", "string", "enum", values);
        }
        if (Collection.class.isAssignableFrom(raw)) {
            ResolvableType itemType = type.asCollection().getGeneric(0);
            return Map.of("type", "array", "items", schemaForType(itemType, schemas, false));
        }
        if (Map.class.isAssignableFrom(raw)) {
            ResolvableType valueType = type.asMap().getGeneric(1);
            Map<String, Object> mapSchema = new LinkedHashMap<>();
            mapSchema.put("type", "object");
            mapSchema.put("additionalProperties", valueType.resolve() == Object.class ? true : schemaForType(valueType, schemas, false));
            return mapSchema;
        }
        if (raw == Object.class) {
            return Map.of("type", "object");
        }

        String schemaName = schemaNameFor(raw);
        if (!schemas.containsKey(schemaName)) {
            schemas.put(schemaName, new LinkedHashMap<>()); // break cycles
            schemas.put(schemaName, buildObjectSchema(raw, schemas));
        }
        return Map.of("$ref", "#/components/schemas/" + schemaName);
    }

    private String schemaNameFor(Class<?> clazz) {
        Class<?> enclosing = clazz.getEnclosingClass();
        return enclosing != null ? enclosing.getSimpleName() + "_" + clazz.getSimpleName() : clazz.getSimpleName();
    }

    private Map<String, Object> buildObjectSchema(Class<?> clazz, Map<String, Map<String, Object>> schemas) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (clazz.isRecord()) {
            for (RecordComponent rc : clazz.getRecordComponents()) {
                boolean treatAsIdString = isIdField(rc.getName()) && (rc.getType() == Long.class || rc.getType() == long.class);
                properties.put(rc.getName(), schemaForType(ResolvableType.forType(rc.getGenericType()), schemas, treatAsIdString));
                if (rc.getType().isPrimitive() || rc.isAnnotationPresent(jakarta.validation.constraints.NotBlank.class)
                        || rc.isAnnotationPresent(jakarta.validation.constraints.NotNull.class)) {
                    required.add(rc.getName());
                }
            }
        } else {
            for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                        continue;
                    }
                    boolean treatAsIdString = isIdField(field.getName()) && (field.getType() == Long.class || field.getType() == long.class);
                    properties.putIfAbsent(field.getName(), schemaForType(ResolvableType.forField(field), schemas, treatAsIdString));
                    if (field.getType().isPrimitive() || field.isAnnotationPresent(jakarta.validation.constraints.NotBlank.class)
                            || field.isAnnotationPresent(jakarta.validation.constraints.NotNull.class)) {
                        if (!required.contains(field.getName())) {
                            required.add(field.getName());
                        }
                    }
                }
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private boolean isIdField(String name) {
        return "id".equals(name) || name.endsWith("Id");
    }
}
