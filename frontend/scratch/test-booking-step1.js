const puppeteer = require('puppeteer-core');
const path = require('path');

const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const brainDir = 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\05967cfe-b20c-4fa3-b630-ab358097e398';

// Robust helper to click and wait for navigation to complete safely
async function safeClickAndNavigate(page, selector, targetUrlSubstring) {
  console.log(`Clicking ${selector} and waiting for navigation to ${targetUrlSubstring}...`);
  try {
    await Promise.all([
      page.click(selector),
      page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 12000 })
    ]);
  } catch (err) {
    console.log(`Navigation promise timed out or failed: ${err.message}. Checking current URL...`);
  }

  // Poll for the URL to change if it hasn't yet
  let currentUrl = page.url();
  let attempts = 0;
  while (!currentUrl.includes(targetUrlSubstring) && attempts < 10) {
    await new Promise(resolve => setTimeout(resolve, 500));
    currentUrl = page.url();
    attempts++;
  }
  console.log(`Navigation finished. Current URL: ${currentUrl}`);
  // Give extra 500ms for dynamic rendering/API calls to load
  await new Promise(resolve => setTimeout(resolve, 500));
}

async function run() {
  const browser = await puppeteer.launch({
    executablePath: chromePath,
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 800 });

  page.on('console', msg => console.log('PAGE LOG:', msg.text()));

  // ==========================================
  // TEST 1: Redirect to login when not authenticated
  // ==========================================
  console.log('--- TEST 1: Redirect unauthenticated user ---');
  console.log('Navigating directly to booking step 1...');
  await page.goto('http://localhost:5500/frontend/pages/customer/booking-step1-vehicle.html', {
    waitUntil: 'networkidle0'
  });

  console.log('Current URL after direct navigation:', page.url());
  await page.screenshot({ path: path.join(brainDir, 'booking_step1_redirect_to_login.png') });
  console.log('Saved unauthenticated redirect screenshot.');

  // ==========================================
  // TEST 2: Successful Login and Navigation
  // ==========================================
  console.log('--- TEST 2: Authenticated user flow ---');
  console.log('Navigating to login page...');
  await page.goto('http://localhost:5500/frontend/pages/login.html', {
    waitUntil: 'networkidle0'
  });

  // Type login credentials
  await page.type('#email', 'customer1@test.com');
  await page.type('#password', 'Admin@2026');
  
  await safeClickAndNavigate(page, 'button[type="submit"]', 'customer/home.html');
  
  // Take screenshot of customer home dashboard
  await page.screenshot({ path: path.join(brainDir, 'customer_home_dashboard.png') });
  console.log('Saved customer home dashboard screenshot.');

  // Go to booking step 1
  console.log('Navigating to booking step 1...');
  await page.goto('http://localhost:5500/frontend/pages/customer/booking-step1-vehicle.html', {
    waitUntil: 'networkidle0'
  });

  console.log('Successfully opened page. URL:', page.url());
  
  // Wait a bit for layout to render completely
  await new Promise(resolve => setTimeout(resolve, 1000));
  
  await page.screenshot({ path: path.join(brainDir, 'booking_step1_initial.png') });
  console.log('Saved booking step 1 initial screenshot.');

  // ==========================================
  // TEST 3: Vehicle selection interaction
  // ==========================================
  console.log('--- TEST 3: Interacting with vehicle selection ---');
  
  // Select "Xe tải 1 tấn"
  console.log('Selecting "Xe tải 1 tấn" (TRUCK_1T)...');
  await page.click('article[data-type="TRUCK_1T"]');
  await new Promise(resolve => setTimeout(resolve, 500));
  
  // Check localStorage value
  const localStorageVal1 = await page.evaluate(() => localStorage.getItem('booking_vehicleType'));
  console.log('Local storage booking_vehicleType value:', localStorageVal1);

  await page.screenshot({ path: path.join(brainDir, 'booking_step1_selected_1T.png') });
  console.log('Saved selection 1T screenshot.');

  // Select "Xe tải 1.5 tấn"
  console.log('Selecting "Xe tải 1.5 tấn" (TRUCK_15T)...');
  await page.click('article[data-type="TRUCK_15T"]');
  await new Promise(resolve => setTimeout(resolve, 500));
  
  const localStorageVal2 = await page.evaluate(() => localStorage.getItem('booking_vehicleType'));
  console.log('Local storage booking_vehicleType value:', localStorageVal2);

  await page.screenshot({ path: path.join(brainDir, 'booking_step1_selected_15T.png') });
  console.log('Saved selection 15T screenshot.');

  // ==========================================
  // TEST 4: Step 2 Pickup interaction & storage
  // ==========================================
  console.log('--- TEST 4: Navigating to Step 2 Pickup ---');
  
  // Click "Tiếp tục" in Step 1 summary box to navigate to Step 2
  await safeClickAndNavigate(page, '.summary-action a', 'booking-step2-pickup.html');

  await new Promise(resolve => setTimeout(resolve, 1000));
  await page.screenshot({ path: path.join(brainDir, 'booking_step2_initial.png') });
  console.log('Saved Step 2 initial screenshot.');

  // Change fields
  console.log('Selecting "Quận Cầu Giấy"...');
  await page.select('#district', 'Quận Cầu Giấy');

  console.log('Changing floor to 5...');
  // Clear floor field and type 5
  await page.evaluate(() => document.getElementById('floor').value = '');
  await page.type('#floor', '5');

  console.log('Changing address to "Tòa nhà Keangnam, đường Phạm Hùng"...');
  await page.evaluate(() => document.getElementById('address').value = '');
  await page.type('#address', 'Tòa nhà Keangnam, đường Phạm Hùng');

  // Click choice pills
  console.log('Clicking "Có thang máy"...');
  await page.click('#elevator-choices span[data-value="true"]');
  
  console.log('Clicking "Xe vào tận cửa"...');
  await page.click('#alley-choices span[data-value="false"]');

  await new Promise(resolve => setTimeout(resolve, 500));
  await page.screenshot({ path: path.join(brainDir, 'booking_step2_filled.png') });
  console.log('Saved Step 2 filled screenshot.');

  // Click continue to go to Step 3
  console.log('Clicking continue to go to Step 3...');
  await safeClickAndNavigate(page, '#continue-btn', 'booking-step3-dropoff.html');

  // Check localStorage values of step 2
  const savedData2 = await page.evaluate(() => {
    return {
      district: localStorage.getItem('booking_pickupDistrict'),
      floor: localStorage.getItem('booking_pickupFloor'),
      address: localStorage.getItem('booking_pickupAddress'),
      hasElevator: localStorage.getItem('booking_pickupHasElevator'),
      hasAlley: localStorage.getItem('booking_pickupHasAlley'),
      lat: localStorage.getItem('booking_pickupLat'),
      lng: localStorage.getItem('booking_pickupLng')
    };
  });
  console.log('Saved pickup data in localStorage:', savedData2);

  await page.screenshot({ path: path.join(brainDir, 'booking_step3_initial.png') });
  console.log('Saved Step 3 initial screenshot.');

  // ==========================================
  // TEST 5: Step 3 Dropoff interaction & storage
  // ==========================================
  console.log('--- TEST 5: Interacting with Step 3 Dropoff ---');

  // Change fields
  console.log('Selecting "Quận Tây Hồ"...');
  await page.select('#district', 'Quận Tây Hồ');

  console.log('Changing floor to 2...');
  await page.evaluate(() => document.getElementById('floor').value = '');
  await page.type('#floor', '2');

  console.log('Changing address to "Số 12 phố Xuân Diệu, Quảng An"...');
  await page.evaluate(() => document.getElementById('address').value = '');
  await page.type('#address', 'Số 12 phố Xuân Diệu, Quảng An');

  // Click choice pills
  console.log('Clicking "Không có thang máy"...');
  await page.click('#elevator-choices span[data-value="false"]');
  
  console.log('Clicking "Có ngõ nhỏ"...');
  await page.click('#alley-choices span[data-value="true"]');

  await new Promise(resolve => setTimeout(resolve, 500));
  await page.screenshot({ path: path.join(brainDir, 'booking_step3_filled.png') });
  console.log('Saved Step 3 filled screenshot.');

  // Click continue to go to Step 4
  console.log('Clicking continue to go to Step 4...');
  await safeClickAndNavigate(page, '#continue-btn', 'booking-step4-details.html');

  // Check localStorage values of step 3
  const savedData3 = await page.evaluate(() => {
    return {
      district: localStorage.getItem('booking_dropoffDistrict'),
      floor: localStorage.getItem('booking_dropoffFloor'),
      address: localStorage.getItem('booking_dropoffAddress'),
      hasElevator: localStorage.getItem('booking_dropoffHasElevator'),
      hasAlley: localStorage.getItem('booking_dropoffHasAlley'),
      lat: localStorage.getItem('booking_dropoffLat'),
      lng: localStorage.getItem('booking_dropoffLng')
    };
  });
  console.log('Saved dropoff data in localStorage:', savedData3);

  await page.screenshot({ path: path.join(brainDir, 'booking_step4_initial.png') });
  console.log('Saved Step 4 initial screenshot.');

  // ==========================================
  // TEST 6: Step 4 Details interaction & storage
  // ==========================================
  console.log('--- TEST 6: Interacting with Step 4 Details ---');

  // Get tomorrow's date format (YYYY-MM-DD)
  const tomorrowStr = await page.evaluate(() => {
    const tom = new Date();
    tom.setDate(tom.getDate() + 1);
    return tom.toISOString().split('T')[0];
  });
  console.log(`Setting date to tomorrow: ${tomorrowStr}`);
  await page.evaluate((val) => document.getElementById('date').value = val, tomorrowStr);

  console.log('Setting time to "10:00"...');
  await page.evaluate(() => document.getElementById('time').value = '10:00');

  // Click porter count
  console.log('Selecting 1 porter ("1 người")...');
  await page.click('#porter-choices span[data-value="1"]');

  // Toggle services (by default "pack" is active. Let's toggle "fragile" as well)
  console.log('Toggling additional service "Bọc đồ dễ vỡ" (fragile)...');
  await page.click('#service-choices span[data-value="fragile"]');

  console.log('Typing notes...');
  await page.evaluate(() => document.getElementById('note').value = '');
  await page.type('#note', 'Cần chuyển đồ cẩn thận, có đồ điện tử giá trị cao.');

  await new Promise(resolve => setTimeout(resolve, 500));
  await page.screenshot({ path: path.join(brainDir, 'booking_step4_filled.png') });
  console.log('Saved Step 4 filled screenshot.');

  // Click continue to go to Step 5
  console.log('Clicking continue to go to Step 5...');
  await safeClickAndNavigate(page, '#continue-btn', 'booking-step5-quote.html');

  // Check localStorage values of step 4
  const savedData4 = await page.evaluate(() => {
    return {
      date: localStorage.getItem('booking_date'),
      time: localStorage.getItem('booking_time'),
      porterCount: localStorage.getItem('booking_porterCount'),
      services: localStorage.getItem('booking_services'),
      note: localStorage.getItem('booking_note')
    };
  });
  console.log('Saved details data in localStorage:', savedData4);

  await page.screenshot({ path: path.join(brainDir, 'booking_step5_initial.png') });
  console.log('Saved Step 5 initial screenshot.');

  // ==========================================
  // TEST 7: Step 5 Quote calculation & storage
  // ==========================================
  console.log('--- TEST 7: Verifying Step 5 Quote Calculation ---');

  // Give a moment for the page load and dynamic fetch to complete
  await new Promise(resolve => setTimeout(resolve, 2000));

  // Retrieve calculated text values from the DOM
  const quoteTexts = await page.evaluate(() => {
    return {
      distance: document.getElementById('distance-display').textContent,
      duration: document.getElementById('duration-display').textContent,
      baseFare: document.getElementById('base-fare-display').textContent,
      peakSurcharge: document.getElementById('peak-surcharge-display').textContent,
      alleySurcharge: document.getElementById('alley-surcharge-display').textContent,
      floorSurcharge: document.getElementById('floor-surcharge-display').textContent,
      porterFee: document.getElementById('porter-fee-display').textContent,
      totalQuote: document.getElementById('total-quote-display').textContent
    };
  });
  console.log('Calculated quote details on screen:', quoteTexts);

  // Check localStorage values of step 5 quote
  const savedQuoteData = await page.evaluate(() => {
    return {
      totalQuote: localStorage.getItem('booking_totalQuote'),
      distanceKm: localStorage.getItem('booking_distanceKm'),
      durationMinutes: localStorage.getItem('booking_durationMinutes')
    };
  });
  console.log('Saved quote data in localStorage:', savedQuoteData);

  await page.screenshot({ path: path.join(brainDir, 'booking_step5_filled.png') });
  console.log('Saved Step 5 filled screenshot.');

  // Click continue to go to Step 6
  console.log('Clicking continue to go to Step 6...');
  await safeClickAndNavigate(page, '#continue-btn', 'booking-step6-payment.html');

  // ==========================================
  // TEST 8: Step 6 Payment checkout & order creation
  // ==========================================
  console.log('--- TEST 8: Verifying Step 6 Checkout ---');

  // Give a moment for page load and wallet balance fetch to complete
  await new Promise(resolve => setTimeout(resolve, 2000));
  await page.screenshot({ path: path.join(brainDir, 'booking_step6_initial.png') });
  console.log('Saved Step 6 initial screenshot.');

  // Retrieve wallet balance displayed
  const walletText = await page.evaluate(() => document.getElementById('wallet-balance').textContent);
  console.log('Wallet balance text displayed:', walletText);

  // Click cash method
  console.log('Selecting Cash payment method ("Tiền mặt")...');
  await page.click('#payment-cash');

  await new Promise(resolve => setTimeout(resolve, 500));
  await page.screenshot({ path: path.join(brainDir, 'booking_step6_selected_cash.png') });
  console.log('Saved Step 6 selected cash screenshot.');

  // Click confirm checkout button
  console.log('Clicking confirm checkout button to create order...');
  await safeClickAndNavigate(page, '#confirm-btn', 'booking-success.html');

  // Verify booking keys have been cleared from localStorage
  const clearedKeys = await page.evaluate(() => {
    return {
      vehicleType: localStorage.getItem('booking_vehicleType'),
      pickupAddress: localStorage.getItem('booking_pickupAddress'),
      dropoffAddress: localStorage.getItem('booking_dropoffAddress'),
      totalQuote: localStorage.getItem('booking_totalQuote')
    };
  });
  console.log('Booking keys in localStorage after checkout (should all be null):', clearedKeys);

  await page.screenshot({ path: path.join(brainDir, 'booking_success_page.png') });
  console.log('Saved booking success page screenshot.');

  await browser.close();
  console.log('All tests completed successfully!');
}

run().catch(console.error);
