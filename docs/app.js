/**
 * EnrollFlow 網頁版：用 CheerpJ 載入 Java 的 CourseSystem，HTML/JS 只負責畫面。
 *
 * 資料流向（單向）：
 *   玩家點按鈕 → callJava() 呼叫 CourseSystem 的方法（每次都要 await）
 *   → 顯示回傳 JSON 的 msg → refresh() 呼叫 getStateJson() → render() 依狀態整個重畫。
 *
 * 規則：
 * - 選課規則（衝堂、學分、候補遞補、復原）全部在 Java，JS 不重算，只負責「怎麼顯示」。
 * - Java 與 JS 之間只傳 int、boolean、String；狀態一律由 Java 組成 JSON 字串。
 * - 畫面上的文字一律用 textContent 放入，不用 innerHTML，避免資料內容被當成 HTML 執行。
 *
 * 示範模式：網址加上 ?demo（例如 http://localhost:8002/?demo）會改讀 demo-state.json，
 * 不載入 Java，幾秒內就能看到填滿資料的畫面，給截圖與調整版面用。示範模式下按鈕不會改變資料。
 *
 * 狀態 JSON 的欄位說明見 CourseSystem.getStateJson() 的 Javadoc。
 * 負責：C 網頁介面。
 */

// ============================================================
// 全域狀態
// ============================================================

/** 是否為示範模式（網址帶 ?demo）。 */
const DEMO = new URLSearchParams(window.location.search).has('demo');

/** 星期的中文，索引 1–5 對應星期一到五（索引 0 不用）。 */
const WEEKDAYS = ['', '一', '二', '三', '四', '五'];

/** 每天節數，與 Java 的 TimeSlot.PERIODS 相同（第 1–10 節）。 */
const PERIODS = 10;

/** CourseSystem 的 Java 物件；示範模式下維持 null。 */
let system = null;

/** 最近一次從 Java（或 demo-state.json）取得的完整狀態。 */
let state = null;

/** 畫面自己的狀態：哪門課被選取、滑鼠停在哪門課、篩選條件。這些不需要 Java 知道。 */
const ui = {
  selectedCode: null,
  hoverCode: null,
  keyword: '',
  weekday: 0,
  grade: 0,      // 0 = 全部年級、2 = 大二、3 = 大三
  failDismissed: false,   // 失敗畫面按過「查看課表」就不再蓋上來（開新局時重設）
  failFocus: false,       // 失敗畫面剛出現、還沒把焦點移到「再玩一次」
};

/**
 * 上一次畫面上「我在哪些課排候補、排第幾位」。
 * 下一次重畫時若某門課從「有順位」變成「已選上」，代表被遞補了，就跳出通知。
 * @type {Map<string, number> | null}
 */
let previousPositions = null;

/** 一個 Java 呼叫進行中時鎖住按鈕，避免連點造成重複呼叫。 */
let busy = false;

// ============================================================
// 小工具
// ============================================================

/**
 * 建立 DOM 元素的簡寫。
 * @param {string} tag 標籤名稱
 * @param {object} [props] 屬性；className、text、dataset、style 有特別處理，on* 會綁事件
 * @param {...(Node|string|null)} children 子節點；字串會轉成文字節點，null 會略過
 * @returns {HTMLElement}
 */
function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) continue;
    if (key === 'className') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key === 'dataset') Object.assign(node.dataset, value);
    else if (key === 'style') Object.assign(node.style, value);
    else if (key.startsWith('on')) node.addEventListener(key.slice(2).toLowerCase(), value);
    else node.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child);
  }
  return node;
}

/** 取得元素的簡寫。 */
const $ = (id) => document.getElementById(id);

/** 數字補零成兩位，例如 3 → "03"，給 Bebas 大數字用。 */
const pad2 = (n) => String(n).padStart(2, '0');

/** 時段編碼的星期，例如 304 → 3（與 Java TimeSlot.dayOf 相同）。 */
const dayOf = (slot) => Math.floor(slot / 100);

/** 時段編碼的節次，例如 304 → 4（與 Java TimeSlot.periodOf 相同）。 */
const periodOf = (slot) => slot % 100;

/** 時段的中文標籤，例如 304 → 「三4」。 */
const slotLabel = (slot) => WEEKDAYS[dayOf(slot)] + periodOf(slot);

/** 年級與必選修的顯示文字，例如「大二必修」。 */
const gradeLabel = (c) => (c.grade === 2 ? '大二' : c.grade === 3 ? '大三' : '') + (c.required ? '必修' : '選修');

/** 依課號找課程物件。 */
const findCourse = (code) => (state ? state.courses.find((c) => c.code === code) : null);

// ============================================================
// 啟動
// ============================================================

/** 啟動：示範模式讀 JSON 檔；一般模式初始化 CheerpJ、載入 Java、建立 CourseSystem。 */
async function start() {
  bootStart();
  try {
    if (DEMO) {
      // 示範模式不載入 Java，但仍然把整段載入動畫播完（約 3 秒），方便錄影與展示
      setBootStep(1, '示範模式：讀取 demo-state.json，不載入 Java。');
      const [response] = await Promise.all([fetch('demo-state.json', { cache: 'no-cache' }), sleep(1300)]);
      state = await response.json();
      setBootStep(2);
      await sleep(800);
      setBootStep(3);
      await sleep(600);
      $('demo-tag').hidden = false;
      $('status-text').textContent = 'DEMO MODE';
    } else {
      setBootStep(1);
      await cheerpjInit();
      // CheerpJ 的虛擬路徑 /app/ 對應網站根目錄。
      // GitHub Pages 專案網址是 https://帳號.github.io/倉庫名/，所以要把網頁所在路徑接在 /app 後面。
      const appDirectory = new URL('.', window.location.href).pathname;
      setBootStep(2);
      const lib = await cheerpjRunLibrary(`/app${appDirectory}`);
      const CourseSystem = await lib.g9.enrollflow.CourseSystem;
      setBootStep(3);
      system = await new CourseSystem();
      state = JSON.parse(await system.getStateJson());
    }
    bindEvents();
    render();          // 主畫面先在背後畫好，載入畫面收起時直接是完整畫面
    await bootFinish();
  } catch (err) {
    bootFail(err);
    console.error(err);
  }
}

// ============================================================
// 載入動畫
// ============================================================
//
// 真實的下載進度拿不到（CheerpJ 沒有提供），所以進度條採「階段上限」做法：
// 每個步驟有一個上限百分比，進度每 80ms 往上限靠近一點（越接近越慢），
// 看起來一直在動、但不會在步驟完成前衝到 100%。步驟真的完成時才換下一個上限。
// 區間依實測分配（2026-11-21，無快取的 Chrome）：下載執行環境約 2 秒、
// 載入 class 約 10 秒、建立課程資料約 2 秒，所以第二步給最大的區間。

/** 各步驟的進度上限（%）。4 = 全部完成。 */
const BOOT_CEILING = { 1: 25, 2: 85, 3: 96, 4: 100 };

/** 每 80ms 往上限靠近的比例。示範模式時間短，要走快一點才看得到進度前進。 */
const BOOT_CREEP = DEMO ? 0.08 : 0.015;

/** 進度條格數，與 style.css 的 repeat(40, …) 相同。 */
const BOOT_CELLS = 40;

/** 等待時輪播的資料結構小知識，順便說明本作每個結構用在哪裡。 */
const BOOT_TIPS = [
  ['QUEUE', '候補名單採佇列機制：先排先補，退選就從隊首 poll()。'],
  ['STACK', '復原功能採堆疊機制：最新操作最先透過 pop() 撤銷。'],
  ['SET', '衝堂檢查靠集合交集：時段 retainAll() 不為空即衝堂。'],
  ['MAP', '課號查課是字典查詢：以課號 key 快速對應課程 value。'],
  ['LIST', '課程清單、個人課表與操作紀錄，皆以清單依序存放。'],
];

/** 使用者設定「減少動態效果」時，跳過所有等待與動畫。 */
const REDUCED_MOTION = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** 載入動畫的執行狀態。 */
const boot = {
  step: 0,          // 目前進行中的步驟
  progress: 0,      // 目前顯示的百分比（0–100，含小數）
  startedAt: 0,     // 開始載入的時間，給計時器用
  stepStartedAt: 0, // 目前步驟開始的時間，給每行右側的耗時用
  tipIndex: 0,
  timer: null,      // 進度條計時器
  tipTimer: null,   // 小知識輪播計時器
  typing: false,    // 標題打字循環是否進行中
};

/** 等待指定毫秒數。 */
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** 秒數格式化成 1 位小數，例如 1234ms → "1.2s"。 */
const seconds = (ms) => (ms / 1000).toFixed(1) + 's';

/** 開始載入動畫：建立進度格、啟動計時器、小知識輪播與標題打字循環。 */
function bootStart() {
  $('boot-bar').replaceChildren(...Array.from({ length: BOOT_CELLS }, () => el('i')));
  boot.startedAt = boot.stepStartedAt = performance.now();
  boot.timer = setInterval(bootTick, 80);
  showTip();
  boot.tipTimer = setInterval(showTip, 3600);
  if (!REDUCED_MOTION) typeTitleLoop();
}

/** 標題要逐字打出的字。 */
const BOOT_WORD = 'ENROLLFLOW';

/** 打字節奏（毫秒）：每打一字、每刪一字、打完停留、刪完停留。 */
const TYPE_SPEED = { type: 110, erase: 45, hold: 3000, blank: 800 };

/**
 * 標題打字循環：逐字打出 ENROLLFLOW → 停住讓游標閃 → 由後往前刪掉 → 停一下 → 重來。
 * boot.typing 變成 false（載入完成或失敗）時停止，並把標題恢復成完整的字。
 */
async function typeTitleLoop() {
  const word = $('boot-word');
  const title = $('boot-title');
  boot.typing = true;
  while (boot.typing) {
    title.classList.add('is-typing');              // 打字中：游標不閃
    for (let i = 1; i <= BOOT_WORD.length && boot.typing; i++) {
      word.textContent = BOOT_WORD.slice(0, i);
      await sleep(TYPE_SPEED.type);
    }
    title.classList.remove('is-typing');           // 打完：游標閃爍
    await sleep(TYPE_SPEED.hold);
    title.classList.add('is-typing');
    for (let i = BOOT_WORD.length - 1; i >= 0 && boot.typing; i--) {
      word.textContent = BOOT_WORD.slice(0, i);
      await sleep(TYPE_SPEED.erase);
    }
    title.classList.remove('is-typing');
    await sleep(TYPE_SPEED.blank);
  }
  stopTitleLoop();
}

/** 停止打字循環，標題直接顯示完整的字。 */
function stopTitleLoop() {
  boot.typing = false;
  $('boot-word').textContent = BOOT_WORD;
  $('boot-title').classList.remove('is-typing');
}

/** 每 80ms 更新一次：進度往上限靠近、重畫進度格、百分比與計時器。 */
function bootTick() {
  const ceiling = BOOT_CEILING[boot.step] || 0;
  if (boot.step === 4) {
    boot.progress = Math.min(100, boot.progress + 5);   // 完成後用固定速度補滿
  } else {
    boot.progress += (ceiling - boot.progress) * BOOT_CREEP;
  }

  // 進度格：已完成的格子是 violet，最前面一格（head）閃爍表示還在跑
  const on = Math.floor(boot.progress / 100 * BOOT_CELLS);
  $('boot-bar').querySelectorAll('i').forEach((cell, i) => {
    cell.className = i < on ? 'is-on' : (i === on && boot.progress < 100 ? 'is-head' : '');
  });

  const pct = Math.floor(boot.progress);
  $('boot-pct').firstChild.textContent = String(pct).padStart(3, '0');
  $('boot-progress').setAttribute('aria-valuenow', String(pct));

  // 計時器 T+mm:ss.s：就算進度變慢，數字一直在走，看得出程式沒有當掉
  const elapsed = (performance.now() - boot.startedAt) / 1000;
  const mm = String(Math.floor(elapsed / 60)).padStart(2, '0');
  const ss = (elapsed % 60).toFixed(1).padStart(4, '0');
  $('boot-clock').textContent = `T+${mm}:${ss}`;
}

/** 換下一則小知識，重播打字動畫。 */
function showTip() {
  const [tag, text] = BOOT_TIPS[boot.tipIndex % BOOT_TIPS.length];
  boot.tipIndex += 1;
  const tip = $('boot-tip');
  tip.replaceChildren(el('b', { text: tag }), text);
  tip.classList.remove('is-new');
  void tip.offsetWidth;   // 強制重新排版，讓動畫可以重播
  tip.classList.add('is-new');
}

/**
 * 切換到下一個步驟：上一步標成 OK 並寫上耗時，這一步開始打字。
 * @param {number} step 進行中的步驟（1–3）；4 表示全部完成
 * @param {string} [note] 額外顯示的說明
 */
function setBootStep(step, note) {
  const now = performance.now();
  for (const li of document.querySelectorAll('#boot-steps li')) {
    const n = Number(li.dataset.step);
    if (n === boot.step && n < step) {
      li.querySelector('.step-time').textContent = seconds(now - boot.stepStartedAt);
    }
    li.classList.toggle('is-done', n < step);
    li.classList.toggle('is-active', n === step);
  }
  if (step === 4) $('boot-steps').lastElementChild.hidden = false;
  boot.step = step;
  boot.stepStartedAt = now;
  if (note) $('loading-msg').textContent = note;
}

/** 全部載入完成：補滿進度條、顯示 READY，然後收起載入畫面、讓主畫面依序浮現。 */
async function bootFinish() {
  setBootStep(4, '載入完成。');
  if (REDUCED_MOTION) {
    boot.progress = 100;                           // 不播動畫，直接滿格
  } else {
    while (boot.progress < 100) await sleep(80);   // 等進度條補滿
  }
  bootTick();
  const ready = $('boot-steps').lastElementChild;
  ready.classList.remove('is-active');
  ready.classList.add('is-done');
  ready.querySelector('.step-time').textContent = 'TOTAL ' + seconds(performance.now() - boot.startedAt);
  clearInterval(boot.timer);
  clearInterval(boot.tipTimer);
  stopTitleLoop();                     // 標題停在完整的 ENROLLFLOW

  if (!REDUCED_MOTION) {
    await sleep(500);                  // 讓人看一眼 READY
    $('loading').classList.add('is-exit');
    await sleep(420);                  // 等卡片收起（對應 style.css 的 wipe-up）
  }
  $('loading').hidden = true;
  const app = $('app');
  app.hidden = false;
  if (!REDUCED_MOTION) {
    app.classList.add('is-enter');
    setTimeout(() => app.classList.remove('is-enter'), 900);
  }
  // 每次進入系統都先看規則：等主畫面浮現得差不多再跳出
  setTimeout(() => openRules($('btn-rules')), REDUCED_MOTION ? 0 : 650);
}

// ------------------------------------------------------------
// 選課規則彈窗
// ------------------------------------------------------------

/** 關閉後要把焦點還給誰（通常是上方的「規則」按鈕）。 */
let rulesReturnFocus = null;

/**
 * 打開規則彈窗。規則裡的數字從 Java 的狀態填入，規則改了文字會跟著變。
 * 開著時主畫面設成 inert：滑鼠點不到、Tab 也跳不進去，焦點只在彈窗裡。
 * @param {HTMLElement} [returnTo] 關閉後焦點回到哪個元素
 */
function openRules(returnTo) {
  if (state) {
    const values = {
      totalDays: state.totalDays,
      classmates: state.studentCount - 1,   // 扣掉玩家自己
      minCredits: state.minCredits,
      maxCredits: state.maxCredits,
    };
    for (const b of document.querySelectorAll('#rules [data-rule]')) {
      if (values[b.dataset.rule] !== undefined) b.textContent = values[b.dataset.rule];
    }
  }
  rulesReturnFocus = returnTo || null;
  $('rules').hidden = false;
  $('app').inert = true;
  // 焦點放在「開始選課」，但不要因此捲到彈窗底部（手機上會跳過前面的規則）
  $('rules').scrollTop = 0;
  $('btn-rules-ok').focus({ preventScroll: true });
}

/** 關閉規則彈窗，焦點還給打開它的按鈕。 */
function closeRules() {
  if ($('rules').hidden) return;
  $('rules').hidden = true;
  $('app').inert = false;
  if (rulesReturnFocus) rulesReturnFocus.focus();
}

/** 載入失敗：停止動畫、進度格改成警示色、顯示原因與排除方法。 */
function bootFail(err) {
  clearInterval(boot.timer);
  clearInterval(boot.tipTimer);
  stopTitleLoop();
  $('boot-bar').classList.add('is-error');
  const msg = $('loading-msg');
  msg.classList.add('is-error');
  msg.textContent = '載入失敗：' + err + '。請確認是用 server.py 或 GitHub Pages 開啟，而不是直接雙擊 HTML 檔。';
}

/** 綁定只需要綁一次的事件（上方列按鈕、篩選、鍵盤快捷鍵）。 */
function bindEvents() {
  $('btn-undo').addEventListener('click', () => callJava('undo', () => system.undo()));
  $('btn-next').addEventListener('click', () => callJava('nextDay', () => system.nextDay()));
  $('btn-new').addEventListener('click', newGame);
  $('btn-rules').addEventListener('click', () => openRules($('btn-rules')));
  $('btn-rules-ok').addEventListener('click', closeRules);
  $('btn-rules-x').addEventListener('click', closeRules);
  // 點彈窗外的半透明背景也能關閉
  $('rules').addEventListener('click', (e) => { if (e.target === $('rules')) closeRules(); });
  $('btn-again').addEventListener('click', newGame);
  $('btn-fail-again').addEventListener('click', newGame);
  $('btn-fail-close').addEventListener('click', closeFail);

  // 搜尋與星期篩選只影響「顯示哪些課」，在 JS 端過濾即可，不必呼叫 Java
  $('search').addEventListener('input', (e) => {
    ui.keyword = e.target.value.trim().toLowerCase();
    renderCourses();
  });
  for (const btn of document.querySelectorAll('#weekday-filter button')) {
    btn.addEventListener('click', () => {
      ui.weekday = Number(btn.dataset.day);
      for (const b of document.querySelectorAll('#weekday-filter button')) {
        b.setAttribute('aria-pressed', String(b === btn));
      }
      renderCourses();
    });
  }
  for (const btn of document.querySelectorAll('#grade-filter button')) {
    btn.addEventListener('click', () => {
      ui.grade = Number(btn.dataset.grade);
      for (const b of document.querySelectorAll('#grade-filter button')) {
        b.setAttribute('aria-pressed', String(b === btn));
      }
      renderCourses();
    });
  }

  // 快捷鍵 U = 復原（上方列按鈕上標示的那個鍵）；正在輸入文字時不觸發
  document.addEventListener('keydown', (e) => {
    // Esc 一律擋掉瀏覽器預設動作：Chrome 的 Esc 等於「停止載入」，會中斷 CheerpJ 在背景使用的連線，
    // 之後所有 Java 呼叫都不會回來，整個系統卡死（2026-11-22 用真實按鍵實測重現）。
    // 擋掉之後搜尋框不會自己清空，所以這裡手動清。
    if (e.key === 'Escape') {
      e.preventDefault();
      if (e.target === $('search') && e.target.value) {
        e.target.value = '';
        ui.keyword = '';
        renderCourses();
      }
    }
    // 快捷鍵用 e.code（實體按鍵位置）判斷，不用 e.key（打出來的字）：
    // 開著注音輸入法時，U 鍵的 e.key 會是「ㄧ」或「Process」，用 e.key 會比對不到
    const typing = e.target instanceof HTMLInputElement;
    const plain = !typing && !e.metaKey && !e.ctrlKey && !e.altKey;   // 避開 Cmd+R 重新整理等組合鍵
    const isKey = (letter) => e.code === 'Key' + letter || e.key.toUpperCase() === letter;
    // R 打開／關閉規則；? 也可以（不少網站用 ?，但要按 Shift，所以主要用 R）
    const rulesKey = plain && (isKey('R') || e.key === '?' || (e.code === 'Slash' && e.shiftKey));

    // 規則彈窗開著時，只處理關閉，其他快捷鍵先不動作
    if (!$('rules').hidden) {
      if (e.key === 'Escape' || rulesKey) closeRules();
      return;
    }
    if (rulesKey) {
      openRules($('btn-rules'));
      return;
    }
    if (e.key === 'Escape' && !$('fail').hidden) {
      closeFail();
      return;
    }
    if (plain && isKey('U')) {
      // 不看按鈕是否停用，直接交給 Java：堆疊空了也會回「沒有可以復原的操作」，按了才有反應
      callJava('undo', () => system.undo());
    }
  });
}

// ============================================================
// 呼叫 Java
// ============================================================

/**
 * 執行一個 Java 呼叫，顯示結果並重畫畫面。
 * @param {string} name 方法名稱（示範模式訊息用）
 * @param {() => Promise<string>} action 回傳 JSON 字串（格式 {"ok":..,"msg":..}）的非同步函式
 * @param {string} [code] 操作的課號；加選額滿時用來顯示「排候補」按鈕
 */
async function callJava(name, action, code) {
  if (DEMO) {
    showMessage(`示範模式只展示畫面，不會呼叫 Java 的 ${name}()。網址去掉 ?demo 就會使用真正的系統。`, true);
    return;
  }
  if (busy) return;
  busy = true;
  setButtonsDisabled(true);
  try {
    const result = JSON.parse(await action());
    // 加選額滿時 Java 會回傳 canWait:true，訊息列直接提供「排候補」按鈕，不跳對話框
    const followUp = result.canWait && code
      ? el('button', {
          className: 'btn btn-violet btn-sm',
          type: 'button',
          text: '排候補',
          onClick: () => callJava('joinWaitlist', () => system.joinWaitlist(code), code),
        })
      : null;
    showMessage(result.msg, result.ok, followUp);
    await refresh();
  } catch (err) {
    showMessage('程式錯誤：' + err, false);
    console.error(err);
  } finally {
    busy = false;
    setButtonsDisabled(false);
    render(); // 恢復按鈕的啟用狀態（例如堆疊為空時復原鍵維持停用）
  }
}

/** 開新局：重建 Java 端的所有資料，畫面狀態一併清空。 */
async function newGame() {
  ui.selectedCode = null;
  ui.failDismissed = false;
  previousPositions = null;
  await callJava('newGame', async () => {
    await system.newGame(0, false);
    return JSON.stringify({ ok: true, msg: '已開新局，回到第 1 天。' });
  });
}

/** 向 Java 取得最新狀態並重畫。 */
async function refresh() {
  state = JSON.parse(await system.getStateJson());
  render();
}

/**
 * 在訊息列顯示文字。
 * @param {string} text 訊息
 * @param {boolean} ok 失敗時改用警示樣式
 * @param {HTMLElement|null} [action] 附在訊息後面的按鈕
 */
function showMessage(text, ok, action = null) {
  const box = $('message');
  box.classList.toggle('is-error', !ok);
  // 先移除再加回 class，讓打字動畫每次都重播
  box.classList.remove('is-flash');
  void box.offsetWidth;
  box.classList.add('is-flash');
  $('message-text').textContent = text;
  $('message-action').replaceChildren(...(action ? [action] : []));
}

function setButtonsDisabled(disabled) {
  for (const btn of document.querySelectorAll('#app button')) {
    // 篩選、通知關閉鈕、規則鈕不需要 Java，呼叫進行中也能按
    if (btn.closest('#weekday-filter, #grade-filter') || btn.classList.contains('toast-close') || btn.id === 'btn-rules') continue;
    btn.disabled = disabled;
  }
}

// ============================================================
// 重畫
// ============================================================

/** 依目前的 state 與 ui 重畫所有區塊。 */
function render() {
  if (!state) return;
  renderTopbar();
  renderResult();
  renderFail();
  renderCourses();
  renderTimetable();
  renderSide();
  renderInspector();
  renderStatusbar();
  detectFilled();
}

/** ① 上方列：天數刻度、學分格、按鈕可否使用。 */
function renderTopbar() {
  const shownDay = Math.min(state.day, state.totalDays);
  $('day').textContent = pad2(shownDay);
  $('total-days').textContent = pad2(state.totalDays);
  $('credits').textContent = pad2(state.credits);
  $('max-credits').textContent = state.maxCredits;

  // 天數刻度：已過的天用 ink，今天用 violet
  $('day-ticks').replaceChildren(...Array.from({ length: state.totalDays }, (_, i) =>
    el('i', { className: i < state.day - 1 ? 'is-past' : i === state.day - 1 ? 'is-today' : '' })));

  // 學分格：每格 1 學分
  $('credit-cells').replaceChildren(...Array.from({ length: state.maxCredits }, (_, i) =>
    el('i', { className: i < state.credits ? 'is-on' : '' })));

  if (!busy) {
    $('btn-undo').disabled = state.undoSize === 0;   // 復原堆疊為空時停用（設計檔 ①）
    $('btn-next').disabled = state.finished;
  }
}

/** 加退選結束時顯示結算列。 */
function renderResult() {
  $('result').hidden = !state.finished;
  if (!state.finished) return;
  const taken = state.courses.filter((c) => c.taken);
  const names = taken.map((c) => `${c.code} ${c.name}`).join('、') || '沒有選到任何課';
  $('result').classList.toggle('is-fail', !state.passed);
  $('result-title').textContent = state.passed ? '加退選結束' : '學分不足';
  $('result-text').textContent = `最終 ${state.credits} 學分，共 ${taken.length} 門：${names}。`
    + (state.passed ? '' : `低於 ${state.minCredits} 學分下限，請申請休學。`);
}

/**
 * 失敗畫面：加退選結束且學分低於下限時蓋在主畫面上（是否及格由 Java 的 isPassed() 判斷）。
 * 剛出現時把焦點移到「再玩一次」，鍵盤使用者不必找按鈕。
 */
function renderFail() {
  const box = $('fail');
  const show = state.finished && !state.passed && !ui.failDismissed;
  const wasHidden = box.hidden;
  box.hidden = !show;
  if (!show) return;

  const { credits, minCredits, maxCredits } = state;
  $('fail-credits').textContent = pad2(credits);
  $('fail-min').textContent = pad2(minCredits);
  $('fail-text').replaceChildren(
    `本學期只選到 ${credits} 學分，還差 ${minCredits - credits} 學分才達到 ${minCredits} 學分下限。`,
    el('br'),
    el('strong', { text: '請申請休學。' }));
  $('fail-bar').replaceChildren(...Array.from({ length: maxCredits }, (_, i) => {
    const n = i + 1;
    const cls = [n <= credits ? 'is-on' : n <= minCredits ? 'is-gap' : '', n === minCredits ? 'is-min' : ''];
    return el('i', { className: cls.join(' ').trim() });
  }));
  // Java 呼叫進行中按鈕是停用的，停用的按鈕拿不到焦點；等 callJava 結束後的那次重畫再移
  if (wasHidden) ui.failFocus = true;
  const again = $('btn-fail-again');
  if (ui.failFocus && !again.disabled) {
    again.focus();
    ui.failFocus = false;
  }
}

/** 關掉失敗畫面，回到主畫面看最後的課表；結算列仍會顯示學分不足。 */
function closeFail() {
  ui.failDismissed = true;
  renderFail();
  $('btn-again').focus();
}

// ------------------------------------------------------------
// ② 課程列表
// ------------------------------------------------------------

/**
 * 判斷一門課從玩家角度看是什麼狀態。順序有意義：已選 > 候補中 > 衝堂 > 額滿 > 可選。
 * @returns {{kind: string, label: string}}
 */
function courseStatus(c) {
  if (c.taken) return { kind: 'taken', label: '已選' };
  if (c.myPosition > 0) return { kind: 'waiting', label: `候補 #${c.myPosition}` };
  if (c.sameSubjectWith) return { kind: 'same', label: `同科 ${c.sameSubjectWith}` };
  if (c.conflictWith) return { kind: 'conflict', label: `衝堂 ${c.conflictWith}` };
  if (c.enrolled >= c.capacity) return { kind: 'full', label: `額滿・候補 ${c.waiting} 人` };
  return { kind: 'open', label: '可選' };
}

/** 依搜尋字與星期篩選，畫出課程列表。 */
function renderCourses() {
  const list = $('course-list');
  const visible = state.courses.filter((c) => {
    const text = `${c.code} ${c.name} ${c.teacher}`.toLowerCase();
    const matchKeyword = !ui.keyword || text.includes(ui.keyword);
    const matchDay = ui.weekday === 0 || c.slots.some((s) => dayOf(s) === ui.weekday);
    const matchGrade = ui.grade === 0 || c.grade === ui.grade;
    return matchKeyword && matchDay && matchGrade;
  });

  list.replaceChildren(...visible.map(courseRow));
  $('course-empty').hidden = visible.length > 0;
}

/** 一門課的列：上半部是可點的摘要，選取後下方展開操作按鈕。 */
function courseRow(c) {
  const status = courseStatus(c);
  const selected = ui.selectedCode === c.code;

  const main = el('button', {
    className: 'course-main',
    type: 'button',
    'aria-expanded': String(selected),
    onClick: () => {
      ui.selectedCode = selected ? null : c.code;
      renderCourses();
      renderTimetable();
      renderInspector();
    },
    // 滑過或鍵盤聚焦時，在週課表上預覽這門課的時段
    onMouseenter: () => setHover(c.code),
    onMouseleave: () => setHover(null),
    onFocus: () => setHover(c.code),
    onBlur: () => setHover(null),
  },
    el('span', { className: 'course-title' },
      el('span', { className: 'course-code', text: c.code }),
      el('span', { className: 'course-name', text: c.name })),
    el('span', { className: `chip chip-${status.kind}`, text: status.label }),
    el('span', { className: 'course-meta' },
      ...[gradeLabel(c), c.teacher, `${c.credits} 學分`, c.slotLabel].map((t) => el('span', { text: t }))),
    el('span', { className: 'course-seats', text: `${c.enrolled}/${c.capacity}` }));

  return el('li', { className: 'course' + (selected ? ' is-selected' : '') },
    main,
    selected ? courseActions(c, status) : null);
}

/** 選取課程後顯示的操作按鈕；依狀態只給有意義的選項，最後的判斷仍交給 Java。 */
function courseActions(c, status) {
  const btn = (label, cls, name, fn) => el('button', {
    className: `btn btn-sm ${cls}`,
    type: 'button',
    text: label,
    onClick: () => callJava(name, fn, c.code),
  });

  const hint = {
    taken: '已在你的課表上。退選後名額會立刻由候補隊首遞補。',
    waiting: `你在候補第 ${c.myPosition} 位，前面有人被遞補或放棄時會往前移。`,
    conflict: `與 ${c.conflictWith} 時段重疊，加選會被拒絕。`,
    same: `已選同科目的 ${c.sameSubjectWith}，同一科只能選一班，加選會被拒絕。要換班請先退選 ${c.sameSubjectWith}。`,
    full: `名額已滿，目前 ${c.waiting} 人候補。排候補會加到隊尾。`,
    open: `剩 ${c.capacity - c.enrolled} 個名額。上課時段含助教課。`,
  }[status.kind];

  const buttons = [];
  if (status.kind === 'taken') {
    buttons.push(btn('退選', 'btn-ghost', 'dropCourse', () => system.dropCourse(c.code)));
  } else if (status.kind === 'waiting') {
    buttons.push(btn('取消候補', 'btn-ghost', 'leaveWaitlist', () => system.leaveWaitlist(c.code)));
  } else {
    buttons.push(btn('加選', 'btn-ink', 'addCourse', () => system.addCourse(c.code)));
    if (status.kind === 'full') {
      buttons.push(btn('排候補', 'btn-violet', 'joinWaitlist', () => system.joinWaitlist(c.code)));
    }
  }

  return el('div', { className: 'course-actions' },
    el('p', { className: 'course-hint' + (status.kind === 'conflict' || status.kind === 'same' ? ' is-alert' : ''), text: hint }),
    ...buttons);
}

/** 更新滑鼠停留的課程，只重畫課表（避免整個列表重建而打斷滑鼠事件）。 */
function setHover(code) {
  if (ui.hoverCode === code) return;
  ui.hoverCode = code;
  renderTimetable();
}

// ------------------------------------------------------------
// ③ 週課表
// ------------------------------------------------------------

/**
 * 把一門課的時段依星期分組，連續節次合併成一段，課表上就能畫成一整塊。
 * 例如 [203, 204, 402] → [{day:2, from:3, len:2}, {day:4, from:2, len:1}]
 */
function slotRuns(slots) {
  const sorted = [...slots].sort((a, b) => a - b);
  const runs = [];
  for (const s of sorted) {
    const last = runs[runs.length - 1];
    if (last && last.day === dayOf(s) && last.from + last.len === periodOf(s)) {
      last.len += 1;
    } else {
      runs.push({ day: dayOf(s), from: periodOf(s), len: 1 });
    }
  }
  return runs;
}

/** 依 grid 行列放置元素：第 1 欄是節次、第 1 列是星期。 */
function place(node, day, period, span = 1) {
  node.style.gridColumn = String(day + 1);
  node.style.gridRow = `${period + 1} / span ${span}`;
  return node;
}

/** 畫週課表：格線、已選的課、預覽中的課（含衝堂標示）。 */
function renderTimetable() {
  const grid = $('timetable');
  const nodes = [el('div', { className: 'tt-corner', style: { gridColumn: '1', gridRow: '1' } })];

  for (let d = 1; d <= 5; d++) {
    nodes.push(el('div', { className: 'tt-head', role: 'columnheader', text: WEEKDAYS[d], style: { gridColumn: String(d + 1), gridRow: '1' } }));
  }
  for (let p = 1; p <= PERIODS; p++) {
    nodes.push(el('div', { className: 'tt-period', role: 'rowheader', text: p, style: { gridColumn: '1', gridRow: String(p + 1) } }));
    for (let d = 1; d <= 5; d++) {
      nodes.push(place(el('div', { className: 'tt-cell', 'aria-hidden': 'true' }), d, p));
    }
  }

  // 預覽：滑鼠停留優先，其次是選取中的課
  const preview = findCourse(ui.hoverCode || ui.selectedCode);
  // 造成衝堂或同科目的那門已選課，課表上加紅框，讓玩家看到「是被哪一門擋住」
  const culprit = preview && !preview.taken ? (preview.sameSubjectWith || preview.conflictWith) : '';

  // 已選的課：連續節次畫成一塊
  for (const c of state.courses.filter((x) => x.taken)) {
    for (const run of slotRuns(c.slots)) {
      const cls = 'tt-block' + (c.code === culprit ? ' is-culprit' : '');
      nodes.push(place(el('div', { className: cls, title: `${c.code} ${c.name}（${c.slotLabel}）` },
        el('b', { text: c.code }),
        el('span', { text: c.name })), run.day, run.from, run.len));
    }
  }

  if (preview) {
    const occupied = new Set(state.occupied);
    for (const run of slotRuns(preview.slots)) {
      // 逐節檢查：這一段只要有一節已被佔用，就整段標成衝堂
      let conflict = false;
      for (let i = 0; i < run.len; i++) {
        if (occupied.has(run.day * 100 + run.from + i)) conflict = true;
      }
      // 同科目別班雖然時段不一定重疊，一樣加選不了，所以也用警示樣式；時段重疊的那段優先標「衝堂」
      const same = !!preview.sameSubjectWith;
      const cls = preview.taken ? 'is-own' : conflict || same ? 'is-conflict' : '';
      const label = preview.taken ? '' : conflict ? '衝堂' : same ? '同科' : preview.code;
      nodes.push(place(el('div', { className: `tt-preview ${cls}`, text: label, 'aria-hidden': 'true' }),
        run.day, run.from, run.len));
    }
  }

  grid.replaceChildren(...nodes);
}

// ------------------------------------------------------------
// ④ 我的候補、操作紀錄
// ------------------------------------------------------------

function renderSide() {
  const waits = state.courses.filter((c) => c.myPosition > 0);
  $('my-waits').replaceChildren(...waits.map((c) =>
    el('li', { className: 'my-wait' },
      el('span', { className: 'ticket', 'aria-label': `第 ${c.myPosition} 位` },
        pad2(c.myPosition), el('small', { text: 'POS' })),
      el('span', { className: 'my-wait-name', text: `${c.code} ${c.name}` }),
      el('span', { className: 'my-wait-meta', text: `共 ${c.waiting} 人候補 · 名額 ${c.enrolled}/${c.capacity}` }))));
  $('wait-empty').hidden = waits.length > 0;

  // 操作紀錄：Java 已經把最新的放在最前面；編號從最舊的 #01 開始算
  const total = state.log.length;
  $('log').replaceChildren(...state.log.map((line, i) =>
    el('li', { dataset: { index: '#' + pad2(total - i) }, text: line })));
}

// ------------------------------------------------------------
// ⑤ 資料結構透視面板
// ------------------------------------------------------------

/**
 * 透視面板要顯示哪一門課的候補佇列：選取中的課優先；
 * 沒選的話，挑「我正在候補」的課；再沒有就挑候補人數最多的課。
 */
function queueCourse() {
  return findCourse(ui.selectedCode)
    || state.courses.find((c) => c.myPosition > 0)
    || [...state.courses].sort((a, b) => b.waiting - a.waiting)[0];
}

function renderInspector() {
  // 最近一次資料結構操作（Java 端的 lastOp）
  const lastOp = $('last-op');
  lastOp.textContent = state.lastOp || '尚無操作';
  lastOp.classList.toggle('is-idle', !state.lastOp);

  // QUEUE：front 在左、rear 在右；玩家自己的號碼牌用 violet
  const c = queueCourse();
  $('queue-course').textContent = c ? `${c.code} ${c.name}` : '';
  const view = $('queue-view');
  if (!c || c.waitlist.length === 0) {
    view.replaceChildren(el('p', { className: 'queue-empty', text: c ? `${c.code} 的候補佇列是空的（isEmpty() = true）。` : '還沒有課程。' }));
  } else {
    const last = c.waitlist.length - 1;
    view.replaceChildren(
      el('span', { className: 'q-end', text: 'FRONT →' }),
      ...c.waitlist.map((name, i) => el('div', {
        className: 'q-item' + (i === 0 ? ' is-front' : '') + (name === '你' ? ' is-me' : ''),
        title: i === 0 ? '下一個 poll() 的人' : '',
      },
        el('span', { className: 'q-num', text: pad2(i + 1) }),
        el('span', { className: 'q-name', text: name }),
        el('span', { className: 'q-tag', text: i === 0 ? 'FRONT' : i === last ? 'REAR' : ' ' }))),
      el('span', { className: 'q-end', text: '← REAR' }));
  }

  // STACK：第 0 筆是頂端，下一次按「復原」會 pop 它
  $('stack-size').textContent = `${state.undoSize}/10`;
  const stack = $('stack-view');
  stack.replaceChildren(...(state.undoTop.length
    ? state.undoTop.map((item) => el('li', {}, el('span', { text: item })))
    : [el('li', { className: 'stack-empty', text: '空堆疊：沒有可以復原的操作' })]));

  // SET：玩家已佔用的時段編碼（星期×100＋節次），附上中文對照
  $('set-size').textContent = `size ${state.occupied.length}`;
  const sorted = [...state.occupied].sort((a, b) => a - b);
  $('set-view').replaceChildren(
    el('span', { className: 'set-brace', text: '{' }),
    ...(sorted.length
      ? sorted.map((s) => el('span', { className: 'set-item' }, String(s), el('small', { text: slotLabel(s) })))
      : [el('span', { className: 'empty', text: ' 空集合 ' })]),
    el('span', { className: 'set-brace', text: '}' }));
}

/** 頁尾狀態列。 */
function renderStatusbar() {
  $('student-count').textContent = state.studentCount;
}

// ------------------------------------------------------------
// ⑥ 遞補通知
// ------------------------------------------------------------

/** 比對上一次的候補順位：原本在候補、現在變成已選上，就是被遞補了。 */
function detectFilled() {
  const now = new Map(state.courses.map((c) => [c.code, c.myPosition]));
  if (previousPositions) {
    for (const c of state.courses) {
      if (c.taken && previousPositions.get(c.code) > 0) {
        showToast(`${c.code} ${c.name}`, `你從候補第 ${previousPositions.get(c.code)} 位被遞補選上了。`);
      }
    }
  }
  previousPositions = now;
}

/** 右上角跳出通知，8 秒後自動消失，也可以手動關閉。 */
function showToast(title, text) {
  // 通知放在訊息列下方，不蓋住上方列的「下一天」「規則」等按鈕；頁面捲下去、上方列看不到時才貼齊頂端
  $('toasts').style.top = Math.max(16, $('message').getBoundingClientRect().bottom + 8) + 'px';
  const toast = el('div', { className: 'toast', role: 'status' },
    el('span', { className: 'toast-icon', 'aria-hidden': 'true', text: '↗' }),
    el('div', {},
      el('p', { className: 'toast-title', text: '遞補成功 · ' + title }),
      el('p', { className: 'toast-text', text })),
    el('button', { className: 'toast-close', type: 'button', 'aria-label': '關閉通知', text: '×', onClick: () => toast.remove() }));
  $('toasts').append(toast);
  setTimeout(() => toast.remove(), 8000);
}

start();
