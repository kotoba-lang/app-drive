<main>
  <section class="toolbar">
    <div>
      <p class="eyebrow">drive.etzhayyim.com</p>
      <h1>Workspace Drive</h1>
    </div>
    <button>Connect</button>
  </section>

  <section class="grid" aria-label="Drive status">
    <article>
      <span>Sync</span>
      <strong>Ready</strong>
      <p>Google Workspace connector and Zeebe jobs are exposed through the edge facade.</p>
    </article>
    <article>
      <span>Recent</span>
      <strong>0 files</strong>
      <p>No local account is connected in this preview session.</p>
    </article>
    <article>
      <span>Security</span>
      <strong>E2EE path</strong>
      <p>File operations stay behind the dispatcher and internal trust boundary.</p>
    </article>
  </section>
</main>

<style>
  :global(body) {
    margin: 0;
    font-family: system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif;
    background: #f5f7fb;
    color: #162033;
  }

  main {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    gap: 1rem;
    padding: clamp(1rem, 4vw, 3rem);
  }

  .toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    border-bottom: 1px solid #d8deea;
    padding-bottom: 1rem;
  }

  .eyebrow {
    margin: 0 0 0.25rem;
    color: #5a6475;
    font-weight: 700;
  }

  h1 {
    margin: 0;
    font-size: clamp(2rem, 6vw, 4.5rem);
  }

  button {
    border: 1px solid #162033;
    border-radius: 8px;
    background: #162033;
    color: white;
    padding: 0.75rem 1rem;
    font: inherit;
    font-weight: 800;
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 0.75rem;
  }

  article {
    border: 1px solid #d8deea;
    border-radius: 8px;
    background: white;
    padding: 1rem;
  }

  span {
    display: block;
    color: #5a6475;
    font-size: 0.8rem;
    font-weight: 800;
    text-transform: uppercase;
  }

  strong {
    display: block;
    margin-top: 0.35rem;
    font-size: 1.35rem;
  }

  p {
    color: #4c5667;
    line-height: 1.55;
  }
</style>
