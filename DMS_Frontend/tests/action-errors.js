// Run against `npm run dev -- --port 15173` with a Playwright Page.
// The Playwright MCP can run this function after removing `export default`.
// Covers: pending controls, failed actions (sonner toast errors), retries, and no unhandled errors.
export default async function checkActionErrors(page) {
  const unhandled = [];
  const onError = (error) => unhandled.push(error.message);
  page.on('pageerror', onError);
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

  async function fail(path, method, trigger, controls, { confirmLabel } = {}) {
    failure = { path, method };
    release = null;
    await trigger.click();
    // The confirm button label becomes "Working…" while pending, so locate it
    // by position (last button in the dialog) instead of by name.
    const dialog = page.getByRole('alertdialog');
    const confirm = confirmLabel ? dialog.getByRole('button').last() : null;
    if (confirm) {
      await dialog.waitFor();
      if (confirmLabel) {
        assert(
          (await confirm.textContent())?.includes(confirmLabel),
          `Last dialog button should be ${confirmLabel}`,
        );
      }
      await confirm.click();
    }
    for (let attempt = 0; !release && attempt < 200; attempt++) await page.waitForTimeout(10);
    assert(release, 'Action did not send the expected request');
    if (confirm) assert(await confirm.isDisabled(), 'Pending dialog action must be disabled');
    for (const control of controls) assert(await control.isDisabled(), 'Pending control must be disabled');
    await release();
    release = null;
    if (confirm) await page.getByRole('alertdialog').waitFor({ state: 'detached' });
    await page
      .locator('[data-sonner-toast]')
      .filter({ hasText: 'Please try again.' })
      .last()
      .waitFor({ timeout: 5000 });
    for (const control of controls) assert(await control.isEnabled(), 'Failed action must allow retry');
  }

  try {
    await page.goto('http://127.0.0.1:15173/documents/1');
    // Modal dialogs hide the rest of the page from the accessibility tree, so
    // background controls are located in the DOM (scoped to <main>) instead of by role.
    const deleteDocument = page.locator('main button[data-variant="destructive"]');
    const editDocument = page.locator('main button[data-variant="outline"]');
    await fail('/api/documents/1', 'DELETE', deleteDocument, [deleteDocument, editDocument], {
      confirmLabel: 'Delete document',
    });
    assert(
      await page.getByRole('heading', { name: 'Report', exact: true }).isVisible(),
      'Failed deletion must keep document visible',
    );
    await deleteDocument.click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Delete document' }).click();
    await page.waitForURL('http://127.0.0.1:15173/');

    await page.goto('http://127.0.0.1:15173/collections');
    const row = page.locator('[data-slot="item"]').filter({ hasText: 'Inbox' });
    const open = row.getByRole('button', { name: 'Inbox', exact: true });
    const openDom = row.locator('button[data-variant="link"]');
    const deleteCollectionDom = row.locator('button[data-variant="destructive"]');
    const rowControls = [
      open,
      row.getByRole('button', { name: 'Rename Inbox' }),
      row.getByRole('button', { name: 'Delete Inbox' }),
    ];
    await fail('/api/collections/1', 'GET', open, rowControls);
    await open.click();
    const detail = page.locator('[data-slot="card"]').filter({ hasText: 'in this collection' });
    await detail.getByText('Inbox', { exact: true }).waitFor();
    const checkbox = detail.getByRole('checkbox', { name: 'Report', exact: true });
    await checkbox.waitFor();
    await fail('/api/collections/1/documents/1', 'PUT', checkbox, [checkbox, ...rowControls]);
    assert(!(await checkbox.isChecked()), 'Failed add must keep membership unchanged');
    await checkbox.click();
    await row.getByText('1 document', { exact: true }).waitFor();
    await fail('/api/collections/1/documents/1', 'DELETE', checkbox, [checkbox, ...rowControls]);
    assert(await checkbox.isChecked(), 'Failed removal must keep membership unchanged');
    await checkbox.click();
    await row.getByText('0 documents', { exact: true }).waitFor();
    const deleteCollection = row.getByRole('button', { name: 'Delete Inbox' });
    await fail('/api/collections/1', 'DELETE', deleteCollection, [openDom, deleteCollectionDom], {
      confirmLabel: 'Delete collection',
    });
    await deleteCollection.click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Delete collection' }).click();
    await page.getByText('No collections yet', { exact: true }).waitFor();
    assert(unhandled.length === 0, `Unhandled errors: ${unhandled.join(', ')}`);
    return 'Passed: five failed actions, toast errors, pending controls, and successful retries';
  } finally {
    if (release) await release();
    await page.unrouteAll({ behavior: 'wait' });
    page.removeListener('pageerror', onError);
  }
}
