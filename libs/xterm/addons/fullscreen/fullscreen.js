/*
 * Fullscreen addon for xterm.js
 *
 * Implements the toggleFullscreen function.
 *
 * If the `fullscreen` argument has been supplied, then
 * if it is true, the fullscreen mode gets turned on,
 * if it is false or null, the fullscreen mode gets turned off.
 *
 * If the `fullscreen` argument has not been supplied, the
 * fullscreen mode is being toggled.
 */
if (typeof goog === 'object') {
  goog.require('xterm');
  goog.provide('xterm.addons.fullscreen');
}

(function (fullscreen) {
  if (typeof exports === 'object' && typeof module === 'object') {
    /*
     * CommonJS environment
     */
    module.exports = fullscreen(require('../../src/xterm'));
  } else if (typeof define == 'function') {
    /*
     * Require.js is available
     */
    define(['../../src/xterm'], fullscreen);
  } else if (typeof goog === 'object') {
    fullscreen(xterm.Xterm);
  } else {
    /*
     * Plain browser environment
     */
    fullscreen(this.Xterm);
  }
})(function (Xterm) {
  var exports = {};
  if (typeof goog === 'object') {
    exports = xterm.addons.fullscreen;
  }

  exports.toggleFullScreen = function (term, fullscreen) {
    var fn;

    if (typeof fullscreen == 'undefined') {
      fn = (term.element.classList.contains('fullscreen')) ? 'remove' : 'add';
    } else if (!fullscreen) {
      fn = 'remove';
    } else {
      fn = 'add';
    }

    term.element.classList[fn]('fullscreen');
  };

  Xterm.prototype.toggleFullscreen = function (fullscreen) {
    exports.toggleFullScreen(this, fullscreen);
  };

  return exports;
});
