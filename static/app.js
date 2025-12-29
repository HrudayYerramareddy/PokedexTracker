/* =========================================================
   POKÉDEX TRACKER — FINAL STABLE VERSION (LZA LOAD FIX)
   ========================================================= */

   const GAMES = [
    { id: "gen1", name: "Red / Blue / Yellow", sections: [{ id: "kanto", name: "Kanto", dex: "kanto" }] },
    { id: "gen2", name: "Gold / Silver / Crystal", sections: [{ id: "johto", name: "Johto", dex: "original-johto" }] },
    { id: "gen3", name: "Ruby / Sapphire / Emerald", sections: [{ id: "hoenn", name: "Hoenn", dex: "hoenn" }] },
    { id: "gen4", name: "Diamond / Pearl / Platinum", sections: [{ id: "sinnoh", name: "Sinnoh", dex: "extended-sinnoh" }] },
    { id: "gen5", name: "Black / White / B2W2", sections: [{ id: "unova", name: "Unova", dex: "updated-unova" }] },
    {
      id: "gen6",
      name: "X / Y",
      sections: [
        { id: "kalos-c", name: "Kalos Central", dex: "kalos-central" },
        { id: "kalos-co", name: "Kalos Coastal", dex: "kalos-coastal" },
        { id: "kalos-m", name: "Kalos Mountain", dex: "kalos-mountain" },
      ],
    },
    { id: "gen7", name: "Sun / Moon / Ultra", sections: [{ id: "alola", name: "Alola", dex: "updated-alola" }] },
    {
      id: "gen8-swsh",
      name: "Sword / Shield",
      sections: [
        { id: "galar", name: "Galar", dex: "galar" },
        { id: "ioa", name: "Isle of Armor", dex: "isle-of-armor" },
        { id: "ct", name: "Crown Tundra", dex: "crown-tundra" },
      ],
    },
    { id: "gen8-pla", name: "Legends: Arceus", sections: [{ id: "hisui", name: "Hisui", dex: "hisui" }] },
    {
      id: "gen9",
      name: "Scarlet / Violet",
      sections: [
        { id: "paldea", name: "Paldea", dex: "paldea" },
        { id: "tm", name: "Kitakami", dex: "kitakami" },
        { id: "id", name: "Blueberry", dex: "blueberry" },
      ],
    },
  
    // ✅ LZA (LOCAL JSON)
    {
      id: "gen10-lza",
      name: "Legends: Z-A",
      local: true,
      sections: [
        { id: "kalos-base", name: "Kalos" },
        { id: "mega-dimension", name: "Mega Dimension" },
      ],
    },
  
    { id: "overall", name: "Overall Dex", sections: [{ id: "national", name: "National Dex", dex: null }] },
  ];
  
  /* ===================== GLOBAL STATE ===================== */
  
  const modeByGame = {};
  GAMES.forEach((g) => (modeByGame[g.id] = "normal"));
  
  let currentGame = GAMES[0];
  let currentSection = currentGame.sections[0];
  
  const dexCache = new Map();
  let overallUnion = [];
  let lzaData = null;
  let lzaLoadError = "";
  
  /* ======================== DOM =========================== */
  
  const gameTabs = document.getElementById("gameTabs");
  const sectionTabs = document.getElementById("sectionTabs");
  const normalBtn = document.getElementById("normalBtn");
  const shinyBtn = document.getElementById("shinyBtn");
  const progress = document.getElementById("progress");
  const grid = document.getElementById("grid");
  
  /* ======================== INIT ========================== */
  
  normalBtn.onclick = () => setMode("normal");
  shinyBtn.onclick = () => setMode("shiny");
  
  init();
  
  async function init() {
    buildGameTabs();
    buildSectionTabs();
    updateModeButtons();
    await buildOverallDex();
    await render();
  }
  
  /* ======================== UI ============================ */
  
  function buildGameTabs() {
    gameTabs.innerHTML = "";
    for (const g of GAMES) {
      const b = document.createElement("button");
      b.className = "tab" + (g.id === currentGame.id ? " active" : "");
      b.textContent = g.name;
      b.onclick = async () => {
        currentGame = g;
        currentSection = g.sections[0];
        grid.innerHTML = "";
        buildGameTabs();
        buildSectionTabs();
        updateModeButtons();
        await render();
      };
      gameTabs.appendChild(b);
    }
  }
  
  function buildSectionTabs() {
    sectionTabs.innerHTML = "";
    for (const s of currentGame.sections) {
      const b = document.createElement("button");
      b.className = "tab" + (s.id === currentSection.id ? " active" : "");
      b.textContent = s.name;
      b.onclick = async () => {
        currentSection = s;
        grid.innerHTML = "";
        buildSectionTabs();
        await render();
      };
      sectionTabs.appendChild(b);
    }
  }
  
  function setMode(m) {
    modeByGame[currentGame.id] = m;
    updateModeButtons();
    render();
  }
  
  function updateModeButtons() {
    normalBtn.classList.toggle("active", mode() === "normal");
    shinyBtn.classList.toggle("active", mode() === "shiny");
  }
  
  function mode() {
    return modeByGame[currentGame.id];
  }
  
  /* ======================== API =========================== */
  
  async function getState() {
    return (await fetch("/api/state")).json();
  }
  
  async function savePokemon(name, normal, shiny) {
    if (shiny) normal = true;
    if (!normal) shiny = false;
  
    await fetch(`/api/pokemon/${name}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ normal, shiny }),
    });
  }
  
  /* ===================== DEX LOAD ========================= */
  
  function speciesId(url) {
    return Number(url.match(/\/pokemon-species\/(\d+)\//)[1]);
  }
  
  function sprite(id, shiny) {
    return `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${shiny ? "shiny/" : ""}${id}.png`;
  }
  
  async function loadDex(dex) {
    if (!dex) return [];
    if (dexCache.has(dex)) return dexCache.get(dex);
  
    const res = await fetch(`https://pokeapi.co/api/v2/pokedex/${dex}`);
    const j = await res.json();
  
    const list = j.pokemon_entries.map((e) => ({
      apiName: e.pokemon_species.name,
      name: title(e.pokemon_species.name),
      num: e.entry_number,
      speciesId: speciesId(e.pokemon_species.url),
    }));
  
    dexCache.set(dex, list);
    return list;
  }
  
  /* ================= LZA LOCAL SUPPORT ==================== */
  
  async function tryFetchJson(url) {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`${url} → HTTP ${res.status}`);
    return res.json();
  }
  
  async function loadLZA() {
    if (lzaData || lzaLoadError) return lzaData;
  
    const candidates = [
      "/data/lza.json",
      "/static/data/lza.json",
      "/lza.json",
      "/static/lza.json",
    ];
  
    let lastErr = "";
    for (const url of candidates) {
      try {
        const j = await tryFetchJson(url);
        // basic shape check
        if (!j || !Array.isArray(j.sections)) throw new Error(`${url} → JSON missing sections[]`);
        lzaData = j;
        lzaLoadError = "";
        return lzaData;
      } catch (e) {
        lastErr = String(e.message || e);
      }
    }
  
    lzaLoadError =
      "Could not load lza.json. Tried: " +
      candidates.join(", ") +
      ". Last error: " +
      lastErr;
  
    return null;
  }
  
  async function loadLocalDex(gameId, sectionId) {
    if (gameId !== "gen10-lza") return [];
    const data = await loadLZA();
    if (!data) return [];
    const section = data.sections.find((s) => s.id === sectionId);
    return section ? section.pokemon : [];
  }
  
  /* ===================== OVERALL DEX ======================= */
  
  async function buildOverallDex() {
    const map = new Map();
  
    for (const g of GAMES) {
      for (const s of g.sections) {
        let list = [];
  
        if (g.local) {
          list = await loadLocalDex(g.id, s.id);
        } else if (s.dex) {
          list = await loadDex(s.dex);
        }
  
        for (const p of list) {
          if (!map.has(p.apiName)) map.set(p.apiName, p);
        }
      }
    }
  
    overallUnion = [...map.values()].sort((a, b) => a.speciesId - b.speciesId);
  }
  
  /* ======================= RENDER ========================= */
  
  async function render() {
    const state = await getState();
    const caught = state.caught || {};
    const shinyMode = mode() === "shiny";
  
    let list = [];
  
    if (currentGame.id === "overall") {
      list = overallUnion;
    } else if (currentGame.local) {
      list = await loadLocalDex(currentGame.id, currentSection.id);
    } else {
      list = await loadDex(currentSection.dex);
    }
  
    grid.innerHTML = "";
  
    // ✅ Show LZA error ON PAGE if it fails to load
    if (currentGame.id === "gen10-lza" && (!list || list.length === 0)) {
      const msg = document.createElement("div");
      msg.className = "card";
      msg.style.padding = "16px";
      msg.innerHTML = `
        <div class="name" style="font-weight:700;">Legends: Z-A is empty</div>
        <div style="margin-top:8px; opacity:0.9;">
          ${lzaLoadError ? lzaLoadError : "Loaded 0 Pokémon from lza.json (file exists but has no entries)."}
        </div>
        <div style="margin-top:8px; opacity:0.9;">
          Fix: make sure your server is serving <b>lza.json</b> from one of these paths:
          <div style="margin-top:6px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace;">
            /data/lza.json<br/>
            /static/data/lza.json<br/>
            /lza.json<br/>
            /static/lza.json
          </div>
        </div>
      `;
      grid.appendChild(msg);
      setProgress(0, 0);
      return;
    }
  
    let done = 0;
  
    for (const p of list) {
      const c = caught[p.apiName] || {};
      const completed = shinyMode ? c.shiny : c.normal;
      if (completed) done++;
  
      const numberToShow =
        currentGame.id === "overall"
          ? String(p.speciesId).padStart(4, "0")
          : String(p.num).padStart(3, "0");
  
      const card = document.createElement("div");
      card.className = "card" + (completed ? " complete" : "");
  
      card.innerHTML = `
        <img src="${sprite(p.speciesId, shinyMode)}">
        <div class="name">${p.name}</div>
        <div class="num">#${numberToShow}</div>
      `;
  
      card.onclick = async () => {
        let normal = !!c.normal;
        let shiny = !!c.shiny;
  
        if (shinyMode) {
          shiny = !shiny;
          if (shiny) normal = true;
        } else {
          normal = !normal;
          if (!normal) shiny = false;
        }
  
        await savePokemon(p.apiName, normal, shiny);
        render();
      };
  
      grid.appendChild(card);
    }
  
    setProgress(done, list.length);
  }
  
  function setProgress(done, total) {
    if (!total) {
      progress.innerHTML = `
        <div class="progressWrap">
          <div class="progressText">0% • 0 / 0</div>
          <div class="progressBar"><div class="progressFill" style="width:0%"></div></div>
        </div>
      `;
      return;
    }
  
    const pct = Math.round((done / total) * 100) || 0;
    progress.innerHTML = `
      <div class="progressWrap">
        <div class="progressText">${pct}% • ${done} / ${total}</div>
        <div class="progressBar">
          <div class="progressFill" style="width:${pct}%"></div>
        </div>
      </div>
    `;
  }
  
  /* ======================= UTIL =========================== */
  
  function title(s) {
    return s
      .split("-")
      .map((p) => p[0].toUpperCase() + p.slice(1))
      .join(" ");
  }
  