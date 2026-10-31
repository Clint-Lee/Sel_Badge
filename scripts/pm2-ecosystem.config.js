module.exports = {
  apps: [
    {
      name: 'media-tools-hot',
      cwd: '/srv/repo/Media_Tools_Hot',
      script: 'server.js',
      env: {
        NODE_ENV: 'production',
        HOST: '127.0.0.1',
        PORT: 3001,
        HOTPOT_ALLOWED_HOSTS: '39.97.50.218,127.0.0.1,localhost',
        HOTPOT_PUBLIC_URL: 'http://39.97.50.218:101',
        BADGE_URL: 'http://127.0.0.1:8080',
        BADGE_PORTAL_URL: 'http://39.97.50.218:100',
        BADGE_CLIENT_ID: 'app_hotpot_video',
        BADGE_CLIENT_SECRET: 'sec_hotpot_video_secret_2026'
      }
    },
    {
      name: 'xiyou-game',
      cwd: '/srv/repo/XiYouOnline',
      script: 'server.js',
      env: {
        NODE_ENV: 'production',
        HOST: '127.0.0.1',
        PORT: 8081,
        XIYOU_PUBLIC_URL: 'http://39.97.50.218:102',
        BADGE_URL: 'http://127.0.0.1:8080',
        BADGE_PORTAL_URL: 'http://39.97.50.218:100',
        BADGE_CLIENT_ID: 'app_xiyou_game',
        BADGE_CLIENT_SECRET: 'sec_xiyou_game_secret_2026'
      }
    }
  ]
};
