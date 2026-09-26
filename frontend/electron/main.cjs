/**
 * -----------------------------------------------------------------------------
 * The Archive - APEX Console Desktop Runner (Electron)
 * -----------------------------------------------------------------------------
 * Launches The Archive as a standalone native desktop application window:
 * - Creates a Frameless/Custom Title-bar Window styled to match Prosperous Universe
 * - Runs with high hardware-accelerated FPS
 * - Seamlessly loads from Vite dev server during development or dist/ in production
 * -----------------------------------------------------------------------------
 */

const { app, BrowserWindow } = require('electron');
const path = require('path');
const http = require('http');

function createWindow() {
  const mainWindow = new BrowserWindow({
    width: 1600,
    height: 1000,
    minWidth: 1024,
    minHeight: 700,
    backgroundColor: '#0a0d11',
    title: 'The Archive // APEX Console',
    autoHideMenuBar: true,
    show: false, // Don't show until ready-to-show to prevent white flash
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
    },
  });

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

  // Check if active Vite dev server is running without triggering Chromium console warnings
  const devReq = http.get('http://localhost:3000', () => {
    mainWindow.loadURL('http://localhost:3000');
  });
  devReq.on('error', () => {
    // Production build mode: load compiled dist/index.html directly
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));
  });
  devReq.setTimeout(400, () => {
    devReq.destroy();
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));
  });
}

// App lifecycle
app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
