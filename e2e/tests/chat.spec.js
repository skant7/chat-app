// @ts-check
const { test, expect } = require('@playwright/test');
const path = require('path');

const suffix = `${Date.now()}`;
const alice = `AliceCI_${suffix}`;
const bob = `BobCI_${suffix}`;
const password = 'secret12';

async function register(page, username, pwd) {
  await page.goto('/');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(pwd);
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByText('Online')).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('#meLabel')).toHaveText(username);
}

async function login(page, username, pwd) {
  await page.goto('/');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(pwd);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByText('Online')).toBeVisible({ timeout: 20_000 });
}

test.describe('Private chat e2e', () => {
  test('rejects login without password', async ({ page }) => {
    await page.goto('/');
    await page.locator('#username').fill(alice);
    await page.getByRole('button', { name: 'Log in' }).click();
    await expect(page.getByRole('alert')).toContainText('Password is required');
  });

  test('register, chat across two users, media, and password reset', async ({ browser, request, baseURL }) => {
    const alicePage = await browser.newPage();
    const bobPage = await browser.newPage();

    await register(alicePage, alice, password);
    await register(bobPage, bob, password);

    const badLogin = await request.post(`${baseURL}/api/auth/login`, {
      data: { username: alice, password: 'wrong-password' },
    });
    expect(badLogin.status()).toBe(401);

    await alicePage.getByPlaceholder('Message someone…').fill(bob);
    await alicePage.getByRole('button', { name: 'Go' }).click();
    await expect(alicePage.locator('#peerLabel')).toHaveText(bob);

    const message = `hello from ${alice}`;
    await alicePage.getByPlaceholder('Private message…').fill(message);
    await alicePage.getByRole('button', { name: 'Send' }).click();
    await expect(alicePage.getByText(message)).toBeVisible();

    await bobPage.getByRole('button', { name: alice }).click();
    await expect(bobPage.getByText(message)).toBeVisible();

    const reply = `ack from ${bob}`;
    await bobPage.getByPlaceholder('Private message…').fill(reply);
    await bobPage.getByRole('button', { name: 'Send' }).click();
    await expect(bobPage.getByText(reply)).toBeVisible();
    await expect(alicePage.getByText(reply)).toBeVisible();

    const pngPath = path.join(__dirname, 'fixtures', 'pixel.png');
    await alicePage.getByRole('button', { name: 'Attach media' }).click();
    await alicePage.locator('#fileInput').setInputFiles(pngPath);
    await expect(alicePage.locator('#attachPreview.visible')).toBeVisible();
    await alicePage.getByPlaceholder('Private message…').fill('see image');
    await alicePage.getByRole('button', { name: 'Send' }).click();
    await expect(alicePage.getByText('see image')).toBeVisible();
    await expect(alicePage.locator('img.media').last()).toBeVisible();

    const imgSrc = await alicePage.locator('img.media').last().getAttribute('src');
    expect(imgSrc).toMatch(/\/api\/media\/\d+/);
    const mediaRes = await request.get(`${baseURL}${imgSrc}`);
    expect(mediaRes.status()).toBe(200);

    await bobPage.getByText('see image').waitFor({ state: 'visible' });

    await alicePage.getByRole('button', { name: 'Log out' }).click();
    await expect(alicePage.getByRole('button', { name: 'Log in' })).toBeVisible();

    await alicePage.getByRole('button', { name: 'Forgot password?' }).click();
    await alicePage.locator('#forgotUsername').fill(alice);
    await alicePage.getByRole('button', { name: 'Send reset token' }).click();
    await expect(alicePage.locator('#resetTokenInput')).not.toHaveValue('');
    const newPassword = 'newpass99';
    await alicePage.locator('#newPassword').fill(newPassword);
    await alicePage.getByRole('button', { name: 'Set new password' }).click();
    await expect(alicePage.getByRole('status')).toContainText(/Password updated/i);

    const oldLogin = await request.post(`${baseURL}/api/auth/login`, {
      data: { username: alice, password },
    });
    expect(oldLogin.status()).toBe(401);

    await login(alicePage, alice, newPassword);

    await alicePage.close();
    await bobPage.close();
  });

  test('shows sent then delivered receipt when peer receives message', async ({ browser }) => {
    const a = `AliceRcpt_${Date.now()}`;
    const b = `BobRcpt_${Date.now()}`;
    const alicePage = await browser.newPage();
    const bobPage = await browser.newPage();

    await register(alicePage, a, password);
    await register(bobPage, b, password);

    await alicePage.getByPlaceholder('Message someone…').fill(b);
    await alicePage.getByRole('button', { name: 'Go' }).click();
    await expect(alicePage.locator('#peerLabel')).toHaveText(b);

    const text = `receipt-check ${Date.now()}`;
    await alicePage.getByPlaceholder('Private message…').fill(text);
    await alicePage.getByRole('button', { name: 'Send' }).click();
    await expect(alicePage.getByText(text)).toBeVisible();

    // Alice's bubble shows a receipt (✓ sent, upgrades to ✓✓ when Bob gets it)
    const aliceBubble = alicePage.locator('.bubble.mine').filter({ hasText: text });
    await expect(aliceBubble.locator('.receipt')).toBeVisible();

    // Bob opens chat and receives the message (triggers delivery ACK)
    await bobPage.getByRole('button', { name: a }).click();
    await expect(bobPage.getByText(text)).toBeVisible();

    // Alice should see delivered receipt (double tick)
    await expect(aliceBubble.locator('.receipt')).toHaveAttribute('data-status', 'DELIVERED', {
      timeout: 15_000,
    });
    await expect(aliceBubble.locator('.receipt')).toHaveText('✓✓');
    await expect(aliceBubble.locator('.receipt')).toHaveAttribute('aria-label', 'Delivered');

    await alicePage.close();
    await bobPage.close();
  });

  test('duplicate registration returns conflict', async ({ request, baseURL }) => {
    const user = `DupCI_${Date.now()}`;
    const first = await request.post(`${baseURL}/api/auth/register`, {
      data: { username: user, password },
    });
    expect(first.status()).toBe(201);
    const second = await request.post(`${baseURL}/api/auth/register`, {
      data: { username: user, password },
    });
    expect(second.status()).toBe(409);
    const body = await second.json();
    expect(body.error).toMatch(/already exists/i);
  });

  test('profile: set about, rename username, keep chat history', async ({ browser, request, baseURL }) => {
    const stamp = Date.now();
    const alice0 = `AliceProf_${stamp}`;
    const alice1 = `AliceRenamed_${stamp}`;
    const bobName = `BobProf_${stamp}`;
    const aboutText = `About Alice ${stamp}`;

    const alicePage = await browser.newPage();
    const bobPage = await browser.newPage();

    await register(alicePage, alice0, password);
    await register(bobPage, bobName, password);

    // Bob messages Alice so there is history tied to the old username
    await bobPage.getByPlaceholder('Message someone…').fill(alice0);
    await bobPage.getByRole('button', { name: 'Go' }).click();
    const hello = `hi before rename ${stamp}`;
    await bobPage.getByPlaceholder('Private message…').fill(hello);
    await bobPage.getByRole('button', { name: 'Send' }).click();
    await expect(bobPage.getByText(hello)).toBeVisible();

    // Alice opens Profile, sets about + renames
    await alicePage.getByRole('button', { name: 'Profile' }).click();
    await expect(alicePage.locator('#profilePanel')).toBeVisible();
    await alicePage.locator('#profileUsername').fill(alice1);
    await alicePage.locator('#profileAbout').fill(aboutText);
    await alicePage.getByRole('button', { name: 'Save' }).click();

    await expect(alicePage.locator('#meLabel')).toHaveText(alice1, { timeout: 15_000 });
    await expect(alicePage.locator('#meAbout')).toHaveText(aboutText);
    await expect(alicePage.getByText('Online')).toBeVisible({ timeout: 20_000 });
    await expect(alicePage.locator('#profilePanel')).not.toHaveClass(/open/);

    // API reflects profile
    const loginRes = await request.post(`${baseURL}/api/auth/login`, {
      data: { username: alice1, password },
    });
    expect(loginRes.status()).toBe(200);
    const { token } = await loginRes.json();
    const profileRes = await request.get(`${baseURL}/api/profile`, {
      headers: { 'X-Auth-Token': token },
    });
    expect(profileRes.status()).toBe(200);
    const profile = await profileRes.json();
    expect(profile.username).toBe(alice1);
    expect(profile.about).toBe(aboutText);

    // Old username no longer logs in
    const oldLogin = await request.post(`${baseURL}/api/auth/login`, {
      data: { username: alice0, password },
    });
    expect(oldLogin.status()).toBe(401);

    // History still available under new name (message rows were cascaded)
    await alicePage.getByPlaceholder('Message someone…').fill(bobName);
    await alicePage.getByRole('button', { name: 'Go' }).click();
    await expect(alicePage.getByText(hello)).toBeVisible({ timeout: 15_000 });

    // Cannot take Bob's username
    await alicePage.getByRole('button', { name: 'Profile' }).click();
    await alicePage.locator('#profileUsername').fill(bobName);
    await alicePage.getByRole('button', { name: 'Save' }).click();
    await expect(alicePage.locator('#profileError')).toContainText(/already exists/i);

    await alicePage.close();
    await bobPage.close();
  });
});
