const path = require("path");
const webpack = require('webpack');
const TerserPlugin = require("terser-webpack-plugin");

module.exports = {
    cache: true,
    mode: 'production', //Enable name-mangling & minify & all other misc optimizations
    
    /*
     * Specific to @meshsdk/core library only
     */
    resolve: {
        fallback: {
            buffer: require.resolve("buffer"),
            stream: require.resolve("stream")
        }
    },
    plugins: [
        new webpack.ProvidePlugin({
            Buffer: ["buffer", "Buffer"]
        })
    ],
    experiments: {
        asyncWebAssembly: true,
        layers: true
    },
    //----------------------------------------
    
    //To disable auto-generating LICENSE.txt for output js
    optimization: {
        minimize: true,
        minimizer: [
            new TerserPlugin({
                terserOptions: {
                    format: {
                        comments: false
                    }
                },
                extractComments: false
            })
        ]
    },
    
    /*
     * entry --> Define your JS file(s) to be compiled. See https://webpack.js.org/concepts/entry-points/
     * devtool --> For debugging transformed code. See https://webpack.js.org/configuration/devtool/
     * 
     * output >>> See https://webpack.js.org/configuration/output/
     * output.path --> Define where to output your compiled file(s) to. 
     *                 For this maven project, output to build path for maven to pack into jar later.
     *                 NOTE: Don't point to your working directory, to avoid cluttering your workspace.
     * output.filename --> See https://webpack.js.org/configuration/output/#outputfilename
     * output.publicPath --> Define path for on-demand-loading or loading external resources (e.g.: webassembly modules .wasm). 
     *                       Otherwise, defaults to server path (e.g.: http//localhost:8080/).
     *                       See https://webpack.js.org/configuration/output/#outputpublicpath
     */
    entry: ['./src/main/frontend/wallet.js'],
//    devtool: 'source-map',
    output: {
        path: path.resolve(__dirname, "target/npm-build-output"),
        filename: 'bundle.js',
        publicPath: '/jw/plugin/org.joget.cardano.lib.CardanoExplorerLinkFormElement/'
    }
};
