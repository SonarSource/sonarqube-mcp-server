import { test as base, expect } from '@playwright/test';

const test = base.extend({
  _pageErrorGuard: [
    async ({ page }, use) => {
      const errors: string[] = [];
      page.on('pageerror', (err) => errors.push(err.message));
      await use();
      expect(errors, errors.join('\n')).toEqual([]);
    },
    { auto: true },
  ],
});

async function selectCursor(page: import('@playwright/test').Page) {
  await page.goto('/config-generator.html');
  await page.locator('#agent').selectOption('cursor');
  await expect(page.locator('#codeOutput')).not.toContainText('Please select a Target Client');
}

test.describe('config-generator.html', () => {
  test('smoke: loads and generates output for Cursor without page errors', async ({ page }) => {
    await selectCursor(page);
    const out = await page.locator('#codeOutput').textContent();
    expect(out).toContain('mcpServers');
    expect(out).toContain('docker');
  });

  test('stdio + Cloud: JSON env includes token and org placeholders', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-stdio').click();
    const out = await page.locator('#codeOutput').textContent();
    expect(out).toContain('SONARQUBE_TOKEN');
    expect(out).toContain('SONARQUBE_ORG');
    expect(out).not.toContain('/api');
  });

  test('SQC + Cursor: MCP URL ends with /mcp and headers include SONARQUBE_ORG', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#orgKey').fill('my-test-org');
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    const out = await page.locator('#codeOutput').textContent();
    expect(out).toMatch(/https:\/\/api\.sonarcloud\.io\/mcp\b/);
    expect(out).not.toContain('api.sonarcloud.io/api');
    expect(out).toContain('SONARQUBE_ORG');
    expect(out).toContain('my-test-org');
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.headers['SONARQUBE_ORG']).toBe('my-test-org');
    expect(parsed.mcpServers.sonarqube.url).toMatch(/\/mcp$/);
  });

  test('HTTP client: workspace mount hidden and CAG checkbox disabled', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#card-http').click();
    await page.locator('#btn-http-client').click();
    const wrap = page.locator('#workspaceMountWrap');
    await expect(wrap).toBeHidden();
    const cag = page.locator('#tool-cag');
    await expect(cag).toBeDisabled();
    await expect(cag).not.toBeChecked();
  });

  test('project key: stdio emits SONARQUBE_PROJECT_KEY; SQC JSON headers omit it', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#card-stdio').click();
    await page.locator('#projectKey').fill('my_project');
    await expect(page.locator('#codeOutput')).toContainText('SONARQUBE_PROJECT_KEY');
    await expect(page.locator('#codeOutput')).toContainText('my_project');

    await page.locator('#card-sqc').click();
    await expect(page.locator('#projectKeyWrap')).toBeHidden();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.headers).toBeDefined();
    expect(parsed.mcpServers.sonarqube.headers.SONARQUBE_PROJECT_KEY).toBeUndefined();
  });
});
