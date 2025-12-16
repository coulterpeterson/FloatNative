const path = require('path');
const CopyPlugin = require('copy-webpack-plugin');

module.exports = (env, argv) => {
  const isDev = argv.mode === 'development';
  const manifestFile = isDev ? 'manifest.dev.json' : 'manifest.json';

  console.log(`Building for ${argv.mode || 'production'} using ${manifestFile}`);

  return {
    mode: argv.mode || 'production',
    devtool: isDev ? 'cheap-module-source-map' : false,
    entry: {
      background: './src/background/index.ts',
      content: './src/content/index.ts',
      popup: './src/popup/index.ts'
    },
    output: {
      path: path.resolve(__dirname, 'dist'),
      filename: '[name].js',
    },
    resolve: {
      extensions: ['.ts', '.js'],
    },
    module: {
      rules: [
        {
          test: /\.ts$/,
          use: 'ts-loader',
          exclude: /node_modules/,
        },
      ],
    },
    plugins: [
      new CopyPlugin({
        patterns: [
          {
            from: 'public',
            to: '.',
            globOptions: {
              ignore: ['**/manifest.json', '**/manifest.dev.json'],
            },
          },
          { from: `public/${manifestFile}`, to: 'manifest.json' },
          { from: 'src/assets', to: 'assets' },
        ],
      }),
    ],
  };
};
