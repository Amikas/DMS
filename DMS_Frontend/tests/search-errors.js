// Run against `npm run dev -- --port 15173` with a Playwright Page.
import assert from 'node:assert/strict';

export default async function checkSearchErrors(page) {
  const documents = [{ id: 1, title: 'HelloWorld', status: 'UPLOADED' }];
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/documents/search') {
      const query = url.searchParams.get('q');
      return query === 'Hello'
        ? route.fulfill({ json: documents })
        : route.fulfill({ status: 503, json: { message: 'Please try again.' } });
    }
    return route.fulfill({ json: documents });
  });

  try {
    await page.goto('http://127.0.0.1:15173/');
    await page.getByRole('table').waitFor();
    const input = page.getByRole('textbox', { name: 'Search documents' });

    // A failed first search must not count the whole archive as matches.
    await input.fill('failure');
    await page.getByText('Search failed.', { exact: true }).waitFor();
    assert.equal(await page.getByRole('table').count(), 0);
    assert.equal(await page.getByText(/^\d+ matches?$/).count(), 0);
    assert.equal(await page.getByText('No matches', { exact: true }).count(), 0);

    await input.fill('Hello');
    await page.getByText('1 match', { exact: true }).waitFor();
    await page.getByRole('table').waitFor();

    // A later failure must not reuse results from a successful query.
    await input.fill('another failure');
    await page.getByText('Search failed.', { exact: true }).waitFor();
    assert.equal(await page.getByRole('table').count(), 0);
    assert.equal(await page.getByText(/^\d+ matches?$/).count(), 0);

    await page.getByRole('button', { name: 'Clear search' }).click();
    await page.getByRole('table').waitFor();
    assert.equal(await page.getByText('Search failed.', { exact: true }).count(), 0);
    return 'Passed: first-search failure, stale-result failure, recovery, and clearing search';
  } finally {
    await page.unrouteAll({ behavior: 'wait' });
  }
}
