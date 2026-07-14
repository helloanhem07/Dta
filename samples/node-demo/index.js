const http = require('http');
const port = Number(process.env.PORT || 3000);
const server = http.createServer((req, res) => {
  res.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify({ ok: true, app: 'BotHost Node demo', uptime: process.uptime() }));
});
server.listen(port, '127.0.0.1', () => console.log(`Demo đang chạy tại http://127.0.0.1:${port}`));
setInterval(() => console.log(new Date().toISOString(), 'Bot vẫn hoạt động'), 10000);
