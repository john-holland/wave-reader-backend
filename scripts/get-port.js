import fetch from 'node-fetch';

async function getAvailablePort() {
  try {
    const response = await fetch('http://localhost:3001/port');
    const data = await response.json();
    return data.port;
  } catch (error) {
    console.error('Error getting port from broker:', error);
    // Fallback to a random port between 3000-3999
    return Math.floor(Math.random() * 1000) + 3000;
  }
}

export default getAvailablePort; 