const http = require('http');
const { exec } = require('child_process');

async function getAvailablePort() {
  return new Promise((resolve, reject) => {
    http.get('http://host.docker.internal:8765/port', (res) => {
      let data = '';
      res.on('data', (chunk) => data += chunk);
      res.on('end', () => {
        try {
          const { port } = JSON.parse(data);
          resolve(port);
        } catch (error) {
          reject(error);
        }
      });
    }).on('error', reject);
  });
}

async function start() {
  try {
    const port = await getAvailablePort();
    console.log(`Starting backend on port ${port}`);
    
    const gradleProcess = exec(`gradle run --args="--port=${port}"`, {
      env: { ...process.env, PORT: port }
    });

    gradleProcess.stdout.pipe(process.stdout);
    gradleProcess.stderr.pipe(process.stderr);

    gradleProcess.on('exit', (code) => {
      process.exit(code);
    });
  } catch (error) {
    console.error('Failed to start backend:', error);
    process.exit(1);
  }
}

start(); 