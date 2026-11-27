import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.wanyue.cloudlane',
  appName: 'Cloudlane',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
    cleartext: true,
    allowNavigation: ['*'],
  },
  plugins: {
    StatusBar: {
      overlaysWebView: false,
    },
  },
}

export default config
