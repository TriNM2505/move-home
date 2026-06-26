const BASE_URL = 'http://localhost:8080';

async function testAPIs() {
  console.log('--- BẮT ĐẦU KIỂM THỬ API ---');

  // 1. Test Customer Login
  console.log('\n1. Đăng nhập Customer (customer1@test.com)...');
  const customerToken = await login('customer1@test.com', 'Admin@2026', 'CUSTOMER');

  // 2. Test Driver Login
  console.log('\n2. Đăng nhập Driver (driver1@movehome.vn)...');
  const driverToken = await login('driver1@movehome.vn', 'Admin@2026', 'DRIVER');

  if (driverToken) {
    // 3. Test GET /api/driver/wallet
    console.log('\n3. Lấy số dư ví Driver...');
    const wallet = await getAuthenticated(driverToken, '/api/driver/wallet');
    console.log('Kết quả Ví:', wallet);

    // 4. Test POST /api/driver/withdrawals
    console.log('\n4. Thử tạo một yêu cầu rút tiền mới...');
    const withdrawal = await postAuthenticated(driverToken, '/api/driver/withdrawals', { amount: 200000 });
    console.log('Kết quả Yêu cầu rút tiền:', withdrawal);
  }

  console.log('\n--- HOÀN THÀNH KIỂM THỬ API ---');
}

async function login(email, password, expectedRole) {
  try {
    const res = await fetch(`${BASE_URL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${await res.text()}`);
    }

    const data = await res.json();
    console.log(`Đăng nhập thành công! User: ${data.user.fullName}, Role: ${data.user.role}`);
    
    if (data.user.role !== expectedRole) {
      console.error(`LỖI: Kỳ vọng role ${expectedRole} nhưng nhận được ${data.user.role}`);
      return null;
    }

    return data.accessToken;
  } catch (err) {
    console.error('Đăng nhập thất bại:', err.message);
    return null;
  }
}

async function getAuthenticated(token, path) {
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      }
    });

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${await res.text()}`);
    }

    return await res.json();
  } catch (err) {
    console.error(`Lỗi GET ${path}:`, err.message);
    return null;
  }
}

async function postAuthenticated(token, path, body) {
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(body)
    });

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${await res.text()}`);
    }

    return await res.json();
  } catch (err) {
    console.error(`Lỗi POST ${path}:`, err.message);
    return null;
  }
}

testAPIs();
