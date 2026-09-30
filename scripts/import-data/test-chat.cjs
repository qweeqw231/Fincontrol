// 临时验证脚本：测试 RAG 实盘知识命中（UTF-8 干净编码）
const http = require('http');

function chat(message) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({ message, userId: 1 });
    const req = http.request(
      {
        hostname: 'localhost',
        port: 8080,
        path: '/api/chat/send',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          'Content-Length': Buffer.byteLength(body),
          'X-User-Id': '1',
        },
      },
      (res) => {
        let data = '';
        res.setEncoding('utf8');
        res.on('data', (c) => (data += c));
        res.on('end', () => resolve({ status: res.statusCode, body: data }));
      }
    );
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

(async () => {
  const msg = process.argv[2] || '我的实盘净值峰值是多少？最大回撤发生在什么时候？';
  console.log('Q:', msg);
  const r = await chat(msg);
  console.log('HTTP', r.status);
  try {
    const j = JSON.parse(r.body);
    const d = j.data || {};
    console.log('intent:', JSON.stringify(d.intentClassification));
    console.log('routedTo:', d.assistantMessage?.routedTo);
    console.log('reply:', (d.assistantMessage?.content || '').slice(0, 600));
  } catch {
    console.log('raw:', r.body.slice(0, 600));
  }
})();