import type { WikipediaNode } from '../types/graph';

const API_URL = 'https://en.wikipedia.org/w/api.php';

export async function fetchWikipediaLinks(title: string): Promise<string[]> {
  const allLinks = new Set<string>();
  let plcontinue: string | null = null;

  do {
    const params = new URLSearchParams({
      action: 'query',
      titles: title,
      prop: 'links',
      pllimit: 'max',
      format: 'json',
      origin: '*',
    });

    if (plcontinue) {
      params.append('plcontinue', plcontinue);
    }

    const response = await fetch(`${API_URL}?${params.toString()}`);
    const data = await response.json();

    if (data.query && data.query.pages) {
      const pages = data.query.pages;
      for (const pageId in pages) {
        const page = pages[pageId];
        if (page.links) {
          for (const link of page.links) {
            allLinks.add(link.title);
          }
        }
      }
    }

    plcontinue = data.continue ? data.continue.plcontinue : null;
  } while (plcontinue);

  return Array.from(allLinks);
}

export async function fetchArticleDetails(titles: string[]): Promise<WikipediaNode[]> {
  const nodes: WikipediaNode[] = [];
  const titleChunks = [];

  for (let i = 0; i < titles.length; i += 50) {
    titleChunks.push(titles.slice(i, i + 50));
  }

  for (const chunk of titleChunks) {
    const params = new URLSearchParams({
      action: 'query',
      titles: chunk.join('|'),
      prop: 'extracts|categories',
      exintro: 'true',
      explaintext: 'true',
      cllimit: 'max',
      format: 'json',
      origin: '*',
    });

    const response = await fetch(`${API_URL}?${params.toString()}`);
    const data = await response.json();

    if (data.query && data.query.pages) {
      const pages = data.query.pages;
      for (const pageId in pages) {
        const page = pages[pageId];
        if (page.missing !== undefined) continue;

        const categories = page.categories?.map((cat: { title: string }) => cat.title) || [];

        let domain = 'Other';
        if (categories.some(c => c.toLowerCase().includes('mathematics'))) {
          domain = 'Mathematics';
        } else if (categories.some(c => c.toLowerCase().includes('physics'))) {
          domain = 'Physics';
        } else if (categories.some(c => c.toLowerCase().includes('computer science'))) {
          domain = 'Computer Science';
        }

        nodes.push({
          id: page.title,
          label: page.title,
          summary: page.extract,
          domain: domain,
        });
      }
    }
  }

  return nodes;
}
