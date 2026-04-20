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
  await expect(page.locator('#codeOutput')).not.toContainText('Please select a target client');
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

  test('snippet placeholders use Pascal case angle brackets', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-stdio').click();
    const out = (await page.locator('#codeOutput').textContent()) || '';
    expect(out).toContain('<YourSonarQubeUserToken>');
    expect(out).toContain('<YourSonarQubeOrganizationKey>');
    expect(out).not.toContain('<YOUR_TOKEN>');
    expect(out).not.toContain('<YOUR_ORG>');
  });

  test('SQC + Cursor: MCP URL ends with /mcp and headers include SONARQUBE_ORG', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#pf-org').fill('my-test-org');
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

  test('HTTP client: workspace mount hidden and CAG toolset hidden', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#card-http').click();
    await page.locator('#btn-http-client').click();
    await expect(page.locator('#workspaceMountWrap')).toBeHidden();
    await expect(page.locator('#tool-cag-label')).toBeHidden();
  });

  test('SQC + defaults: generated headers omit SONARQUBE_TOOLSETS', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.headers).toBeDefined();
    expect(parsed.mcpServers.sonarqube.headers.SONARQUBE_TOOLSETS).toBeUndefined();
    // And the serialized JSON should not mention it at all
    expect(out).not.toContain('SONARQUBE_TOOLSETS');
  });

  test('SQC: unchecking a default toolset triggers SONARQUBE_TOOLSETS header', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    // Uncheck a default that IS available for SQC
    await page.locator('#tool-issues').uncheck();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.headers.SONARQUBE_TOOLSETS).toBeDefined();
    expect(parsed.mcpServers.sonarqube.headers.SONARQUBE_TOOLSETS).not.toContain('issues');
  });

  test('SQC transport: analysis and non-default toolsets are hidden', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-sqc').click();
    // Excluded for SQC:
    await expect(page.locator('#tool-analysis-label')).toBeHidden();
    await expect(page.locator('#tool-cag-label')).toBeHidden();
    await expect(page.locator('#tool-sources-label')).toBeHidden();
    await expect(page.locator('#tool-languages-label')).toBeHidden();
    await expect(page.locator('#tool-portfolios-label')).toBeHidden();
    await expect(page.locator('#tool-system-label')).toBeHidden();
    await expect(page.locator('#tool-webhooks-label')).toBeHidden();
    // Available for SQC:
    await expect(page.locator('#tool-projects-label')).toBeVisible();
    await expect(page.locator('#tool-issues-label')).toBeVisible();
    await expect(page.locator('#tool-security-hotspots-label')).toBeVisible();
    await expect(page.locator('#tool-quality-gates-label')).toBeVisible();
    await expect(page.locator('#tool-rules-label')).toBeVisible();
    await expect(page.locator('#tool-duplications-label')).toBeVisible();
    await expect(page.locator('#tool-measures-label')).toBeVisible();
    await expect(page.locator('#tool-dependency-risks-label')).toBeVisible();
    await expect(page.locator('#tool-coverage-label')).toBeVisible();
  });

  test('step 3 is called "Transport mode" (sentence case)', async ({ page }) => {
    await page.goto('/config-generator.html');
    // Wait for the renderer to populate the UI (toolsets grid is built from JSON)
    await expect(page.locator('#tool-projects')).toBeAttached();
    await expect(page.locator('.card-title').filter({ hasText: 'Transport mode' })).toBeVisible();
    await expect(page.locator('.card-title').filter({ hasText: 'Connection Mode' })).toHaveCount(0);
  });

  test('SQC transport is labeled "embedded" not "official"', async ({ page }) => {
    await page.goto('/config-generator.html');
    const sqcCard = page.locator('#card-sqc .radio-title');
    await expect(sqcCard).toContainText('embedded');
    await expect(sqcCard).not.toContainText('Official');
  });

  test('user token label uses "User token (optional)"', async ({ page }) => {
    await page.goto('/config-generator.html');
    const label = page.locator('label[for="token"]');
    await expect(label).toContainText('User token');
    await expect(label).toContainText('(optional)');
    await expect(label).not.toContainText('but recommended');
  });

  test('footer has copyright, docs link, CLI cross-link, llms.txt, and GitHub', async ({ page }) => {
    await page.goto('/config-generator.html');
    const footer = page.locator('footer.footer');
    const year = new Date().getFullYear().toString();
    await expect(footer).toContainText(`© ${year} SonarSource Sàrl`);
    await expect(footer.locator('a[href="https://docs.sonarsource.com/sonarqube-mcp-server"]')).toBeVisible();
    await expect(footer.locator('a[href="https://cli.sonarqube.com"]')).toBeVisible();
    await expect(footer.locator('a[href="llms.txt"]')).toBeVisible();
    await expect(footer.locator('a[href="https://github.com/SonarSource/sonar-mcp-server"]')).toBeVisible();
    // config-flow.json is intentionally not advertised to end users
    await expect(footer.locator('a[href="config-flow.json"]')).toHaveCount(0);
  });

  test('llms.txt (AI-readable reference) is served', async ({ page, request }) => {
    const response = await request.get('/llms.txt');
    expect(response.status()).toBe(200);
    const body = await response.text();
    expect(body).toContain('SonarQube MCP Server');
    expect(body).toContain('<YourSonarQubeUserToken>');
    expect(body).toContain('SONARQUBE_TOOLSETS');
  });

  test('llms.txt is pure ASCII (no mojibake-prone characters)', async ({ request }) => {
    // LLMs or curl|less pipelines may read llms.txt as Latin-1, turning em-dashes
    // and arrows into mojibake (e.g. "—" becomes "â€”"). Keep the file ASCII-only.
    const response = await request.get('/llms.txt');
    expect(response.status()).toBe(200);
    const body = await response.text();
    const offenders: { char: string; code: string; line: number; context: string }[] = [];
    const lines = body.split('\n');
    lines.forEach((line, i) => {
      for (const ch of line) {
        if (ch.charCodeAt(0) > 127) {
          offenders.push({
            char: ch,
            code: 'U+' + ch.charCodeAt(0).toString(16).toUpperCase().padStart(4, '0'),
            line: i + 1,
            context: line.trim(),
          });
        }
      }
    });
    expect(
      offenders,
      'llms.txt must be pure ASCII. Non-ASCII characters found:\n' +
        offenders.map(o => `  line ${o.line}: ${o.code} '${o.char}' in "${o.context}"`).join('\n')
    ).toEqual([]);
  });

  test('nav has a home link, GitHub link, llms.txt link, and theme toggle', async ({ page }) => {
    await page.goto('/config-generator.html');
    const nav = page.locator('nav.nav');
    await expect(nav.locator('a.logo')).toContainText('SonarQube MCP Server');
    await expect(nav.locator('a.nav-link[href="index.html"]')).toBeVisible();
    await expect(nav.locator('a.nav-link[href="llms.txt"]')).toBeVisible();
    await expect(nav.locator('a.nav-link[href*="github.com"]')).toBeVisible();
    await expect(nav.locator('#themeToggle')).toBeVisible();
  });

  test('theme toggle switches to light mode and persists', async ({ page }) => {
    await page.goto('/config-generator.html');
    // Starts in dark mode (no data-theme attribute)
    expect(await page.locator('html').getAttribute('data-theme')).toBeNull();
    await page.locator('#themeToggle').click();
    expect(await page.locator('html').getAttribute('data-theme')).toBe('light');
    // Reload and verify persistence via localStorage
    await page.reload();
    expect(await page.locator('html').getAttribute('data-theme')).toBe('light');
    // Toggle back
    await page.locator('#themeToggle').click();
    expect(await page.locator('html').getAttribute('data-theme')).toBeNull();
  });

  test('favicon and logo assets are served', async ({ request }) => {
    const favicon = await request.get('/assets/favicon.ico');
    expect(favicon.status()).toBe(200);
    const darkLogo = await request.get('/assets/Sonar_Mark_Dark%20Backgrounds.png');
    expect(darkLogo.status()).toBe(200);
    const lightLogo = await request.get('/assets/Sonar_Mark_Light%20Backgrounds.png');
    expect(lightLogo.status()).toBe(200);
  });

  test('external stylesheet is served and defines the CLI design tokens', async ({ request }) => {
    const response = await request.get('/assets/style.css');
    expect(response.status()).toBe(200);
    const body = await response.text();
    expect(body).toContain('--bg: #0f1117');
    expect(body).toContain('--accent: #126ed3');
    expect(body).toContain('[data-theme="light"]');
  });

  test('agent dropdown is sorted alphabetically by label', async ({ page }) => {
    await page.goto('/config-generator.html');
    // Wait for the renderer to populate the dropdown from config-flow.json
    await expect(page.locator('#tool-projects')).toBeAttached();
    const labels = await page.locator('#agent option').evaluateAll(opts =>
      opts.filter(o => (o as HTMLOptionElement).value !== '').map(o => o.textContent!.trim())
    );
    const sorted = [...labels].sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
    expect(labels).toEqual(sorted);
  });

  test('"SonarQube MCP Server" product name is always capitalized correctly', async ({ page, request }) => {
    // Guard against lowercase "server" slipping into user-facing copy.
    // The product name must always appear as "SonarQube MCP Server".
    const pages = [
      '/config-generator.html',
      '/config-flow.json',
      '/llms.txt',
    ];
    for (const p of pages) {
      const res = await request.get(p);
      expect(res.status(), `${p} returned ${res.status()}`).toBe(200);
      const body = await res.text();
      expect(body, `${p} contains "SonarQube MCP server" (wrong casing)`).not.toMatch(/SonarQube MCP server/);
    }
  });

  test('Antigravity HTTP uses serverUrl (not url) and no type field', async ({ page }) => {
    // Per https://antigravity.google/docs/mcp, HTTP transport uses "serverUrl"
    // and has no "type" field (transport inferred from which key is present).
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('antigravity');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.serverUrl).toBeDefined();
    expect(parsed.mcpServers.sonarqube.url).toBeUndefined();
    expect(parsed.mcpServers.sonarqube.type).toBeUndefined();
  });

  test('Windsurf HTTP uses serverUrl (not url) and no type field', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('windsurf');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.serverUrl).toBeDefined();
    expect(parsed.mcpServers.sonarqube.url).toBeUndefined();
    expect(parsed.mcpServers.sonarqube.type).toBeUndefined();
  });

  test('Gemini HTTP uses httpUrl (not url)', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('gemini');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.httpUrl).toBeDefined();
    expect(parsed.mcpServers.sonarqube.url).toBeUndefined();
  });

  test('VS Code uses root key "servers" (not mcpServers)', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('vscode');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-stdio').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.servers).toBeDefined();
    expect(parsed.mcpServers).toBeUndefined();
  });

  test('Kiro HTTP has no type field', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('kiro');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.url).toBeDefined();
    expect(parsed.mcpServers.sonarqube.type).toBeUndefined();
  });

  test('Copilot CLI stdio uses type:"local" and tools:["*"]', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('copilot-cli');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-stdio').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.type).toBe('local');
    expect(parsed.mcpServers.sonarqube.tools).toEqual(['*']);
  });

  test('GitHub Copilot cloud agent stdio env values are COPILOT_MCP_* references', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('copilot-agent');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-stdio').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.env.SONARQUBE_TOKEN).toBe('COPILOT_MCP_SONARQUBE_TOKEN');
    expect(parsed.mcpServers.sonarqube.env.SONARQUBE_ORG).toBe('COPILOT_MCP_SONARQUBE_ORG');
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
