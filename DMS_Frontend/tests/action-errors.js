// Run against `npm run dev -- --port 15173` with a Playwright Page.
// The Playwright MCP can run this function after removing `export default`.
export default async function checkActionErrors(page) {
  const unhandled = [];
  const onError = (error) => unhandled.push(error.message);
  page.on('pageerror', onError);
  await page.addInitScript(() => { window.confirm = () => true; });
  let members = [];
  let deleted = false;
  let failure = null;
  let release;
  const collection = () => ({ id: 1, name: 'Inbox', documentIds: members });

  await page.route('**/api/**', async (route) => {
    const path = route.request().url().replace(/^https?:\/\/[^/]+/, '');
    const method = route.request().method();
    if (failure && failure.path === path && failure.method === method) {
      failure = null;
      await new Promise((resolve) => {
        release = async () => {
          await route.fulfill({ status: 503, json: { message: 'Please try again.' } });
          resolve();
        };
      });
      return;
    }
    if (path === '/api/documents') return route.fulfill({ json: [{ id: 1, title: 'Report', status: 'UPLOADED' }] });
    if (path === '/api/documents/1') {
      return method === 'DELETE'
        ? route.fulfill({ status: 204 })
        : route.fulfill({ json: { id: 1, title: 'Report', status: 'UPLOADED' } });
    }
    if (path === '/api/collections') return route.fulfill({ json: deleted ? [] : [collection()] });
    if (path === '/api/collections/1/documents/1') {
      members = method === 'DELETE' ? [] : [1];
      return method === 'DELETE' ? route.fulfill({ status: 204 }) : route.fulfill({ json: collection() });
    }
    if (path === '/api/collections/1' && method === 'DELETE') {
      deleted = true;
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: collection() });
  });

  function assert(condition, message) {
    if (!condition) throw new Error(message);
  }

  async function fail(path, method, action, controls, errorContainer) {
    failure = { path, method };
    release = null;
    await action.click();
    for (let attempt = 0; !release && attempt < 100; attempt++) await page.waitForTimeout(10);
    assert(release, 'Action did not send the expected request');
    for (const control of controls) assert(await control.isDisabled(), 'Pending control must be disabled');
    await release();
    release = null;
    await errorContainer.getByRole('alert').filter({ hasText: 'Please try again.' }).waitFor({ timeout: 3000 });
    for (const control of controls) assert(await control.isEnabled(), 'Failed action must allow retry');
  }

  try {
    await page.goto('http://127.0.0.1:15173/documents/1');
    const deleteDocument = page.getByRole('button', { name: /^Delet/ });
    await fail('/api/documents/1', 'DELETE', deleteDocument,
      [deleteDocument, page.getByRole('button', { name: 'Edit', exact: true })], page.locator('main'));
    assert(await page.getByRole('heading', { name: 'Report', exact: true }).isVisible(), 'Failed deletion must keep document visible');
    await deleteDocument.click();
    await page.waitForURL('http://127.0.0.1:15173/');

    await page.goto('http://127.0.0.1:15173/collections');
    const row = page.locator('li.row-card');
    const open = row.getByRole('button', { name: /^Inbox/ });
    const rowControls = [open, row.getByRole('button', { name: 'Rename', exact: true }), row.getByRole('button', { name: 'Delete', exact: true })];
    await fail('/api/collections/1', 'GET', open, rowControls, row);
    await open.click();
    const detail = page.locator('.card').filter({ has: page.getByRole('heading', { name: 'Inbox — documents', exact: true }) });
    const checkbox = detail.getByRole('checkbox', { name: 'Report', exact: true });
    await checkbox.waitFor();
    await fail('/api/collections/1/documents/1', 'PUT', checkbox, [checkbox, ...rowControls], detail);
    assert(!await checkbox.isChecked(), 'Failed add must keep membership unchanged');
    await checkbox.click();
    await row.getByRole('button', { name: 'Inbox (1)', exact: true }).waitFor();
    await fail('/api/collections/1/documents/1', 'DELETE', checkbox, [checkbox, ...rowControls], detail);
    assert(await checkbox.isChecked(), 'Failed removal must keep membership unchanged');
    await checkbox.click();
    await row.getByRole('button', { name: 'Inbox (0)', exact: true }).waitFor();
    const deleteCollection = row.getByRole('button', { name: 'Delete', exact: true });
    await fail('/api/collections/1', 'DELETE', deleteCollection, [checkbox, ...rowControls], row);
    await deleteCollection.click();
    await page.getByText('No collections yet.', { exact: true }).waitFor();
    assert(unhandled.length === 0, `Unhandled errors: ${unhandled.join(', ')}`);
    return 'Passed: five failed actions, inline errors, pending controls, and successful retries';
  } finally {
    if (release) await release();
    await page.unrouteAll({ behavior: 'wait' });
    page.removeListener('pageerror', onError);
  }
}
