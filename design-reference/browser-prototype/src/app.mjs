import { cameras, zoneCameras, displayProfiles, tileCounts } from './config.mjs';
import { ViewController } from './view-controller.mjs';

const controller = new ViewController({ cameras: cameras.map(camera => camera.id), zoneCameras });
const stage = document.querySelector('#camera-stage');
const autoButton = document.querySelector('#auto-mode');
const gridButton = document.querySelector('#grid-mode');
const description = document.querySelector('#view-description');
const countdown = document.querySelector('#countdown');
const horseSelect = document.querySelector('#horse');
const zoneSelect = document.querySelector('#zone');
const profileSelect = document.querySelector('#display-profile');
const tileCountSelect = document.querySelector('#tile-count');
const activityCameraSelect = document.querySelector('#activity-camera');
const demoLocations = new Map();
let layoutSignature = '';
let lastHeartbeat = 0;
let selectedProfile = displayProfiles[0].id;
const limits = Object.fromEntries(displayProfiles.map(profile => [profile.id, profile.visibleLimit]));
const storageKey = 'fieldview.displayProfiles.v1';
try {
  const saved = JSON.parse(localStorage.getItem(storageKey));
  if (displayProfiles.some(profile => profile.id === saved?.selectedProfile)) selectedProfile = saved.selectedProfile;
  for (const profile of displayProfiles) {
    if (tileCounts.includes(saved?.limits?.[profile.id])) limits[profile.id] = saved.limits[profile.id];
  }
} catch { /* Screen choices still work when browser storage is unavailable. */ }
for (const profile of displayProfiles) profileSelect.add(new Option(profile.name, profile.id));
for (const count of tileCounts) tileCountSelect.add(new Option(String(count), String(count)));
for (const camera of cameras) activityCameraSelect.add(new Option(camera.name, camera.id));
profileSelect.value = selectedProfile;

function applyProfile() {
  controller.setVisibleLimit(limits[selectedProfile]);
  tileCountSelect.value = String(limits[selectedProfile]);
  document.querySelector('#display-name').textContent = `${displayProfiles.find(profile => profile.id === selectedProfile).name.toUpperCase()} · SAMPLE`;
}

function saveProfiles() {
  try { localStorage.setItem(storageKey, JSON.stringify({ selectedProfile, limits })); } catch { /* Optional persistence. */ }
}
applyProfile();

// Original vector scenes stand in for the video surfaces. These are never live images.
function scene(camera) {
  const palettes = {
    barn: ['#8ea7a6', '#465b49', '#5a6845', '#849063'],
    yard: ['#a7b8b2', '#526958', '#708161', '#96a275'],
    pasture: ['#b1bfb2', '#5a6b53', '#788b5a', '#9ca575'],
    gate: ['#90a8a4', '#455f50', '#667550', '#88916a'],
  };
  const [sky, trees, field, foreground] = palettes[camera.scene];
  const fence = '<g fill="none" stroke="#c0c5a1" stroke-width="3" opacity=".65"><path d="M0 245L640 232M0 260L640 250"/><path d="M35 230v47m95-50v47m95-50v47m95-51v47m95-49v47m95-50v47m95-49v47"/></g>';
  const barn = '<path fill="#584c42" d="M62 168h162v101H62z"/><path fill="#37433d" d="M40 169l102-64 105 64z"/><path fill="#26382f" d="M119 200h51v69h-51z"/><path fill="#899384" d="M83 188h22v25H83zm102 0h22v25h-22z"/><path fill="none" stroke="#777362" stroke-width="2" d="M68 174v89m12-89v89m12-89v89m12-89v89m77-89v89m12-89v89m12-89v89m12-89v89"/>';
  const horse = '<g fill="#293a2d"><path d="M402 243c-11-10-37-9-48-1l-9 7-7 32 5 1 10-23 4 1 1 24h5l2-24 28-1 7 24h5l-3-32 6-15 14-3-1-7-13-6-5-11-5 2 1 17z"/><path d="M351 244q-13 2-12 26l-5 5q-3-27 13-34z"/></g>';
  const gate = '<path fill="#a6a18a" d="M259 360l73-160h31l105 160z"/><g stroke="#c1c6a9" stroke-width="4" fill="none"><path d="M228 240h195v50H228zM228 265h195m-160-25v50m34-50v50m34-50v50m34-50v50m34-50v50"/><path stroke-width="8" d="M223 225v78m205-78v78"/></g>';
  return `<svg class="scene" viewBox="0 0 640 360" preserveAspectRatio="xMidYMid slice" aria-hidden="true"><defs><linearGradient id="sky-${camera.id}" x2="0" y2="1"><stop stop-color="${sky}"/><stop offset="1" stop-color="#d1ccb0"/></linearGradient><linearGradient id="field-${camera.id}" x2="0" y2="1"><stop stop-color="${field}"/><stop offset="1" stop-color="${foreground}"/></linearGradient></defs><path fill="url(#sky-${camera.id})" d="M0 0h640v360H0z"/><path fill="${trees}" d="M0 162l27-28 18 15 31-32 31 14 27-23 38 26 39-15 33 19 28-30 34 25 43-16 26 27 36-23 33 7 29-15 33 22 42-19 27 21 44-26 29 16 31-10v137H0z"/><path fill="url(#field-${camera.id})" d="M0 179q140-16 295 8t345-9v182H0z"/><path fill="none" stroke="#b4b590" stroke-width="12" opacity=".14" d="M0 300Q300 240 640 290M0 330Q300 270 640 320M0 360Q300 300 640 350"/>${camera.scene === 'barn' ? barn : ''}${camera.scene === 'gate' ? gate : fence}${camera.scene === 'pasture' || camera.scene === 'barn' ? horse : ''}${camera.scene === 'yard' ? '<path fill="#6b7362" d="M450 161h131v94H450z"/><path fill="#414e44" d="M432 163l82-48 85 48z"/><path fill="#323f35" d="M481 188h26v67h-26z"/>' : ''}<path fill="#152f25" opacity=".16" d="M0 0h640v360H0z"/></svg>`;
}

const tiles = new Map(cameras.map(camera => {
  const tile = document.createElement('button');
  tile.type = 'button';
  tile.className = 'camera-tile';
  tile.dataset.camera = camera.id;
  tile.setAttribute('aria-label', `Enlarge ${camera.name}`);
  tile.innerHTML = `${scene(camera)}<div class="tile-top"><span class="camera-number">CAM ${camera.number}</span><span class="event-badge" hidden></span></div><div class="tile-bottom"><span class="camera-area">${camera.area} · ILLUSTRATED VIEW</span><span class="camera-name">${camera.name}</span></div>`;
  tile.addEventListener('click', () => { controller.setMode('pin', camera.id); render(); });
  stage.append(tile);
  return [camera.id, tile];
}));

function render() {
  const now = Date.now();
  // Simulate periodic beacon reports while a demo horse remains in a chosen zone.
  if (now - lastHeartbeat >= 10000) {
    for (const [horse, zone] of demoLocations) controller.location(horse, zone, now);
    lastHeartbeat = now;
  }
  const view = controller.snapshot(now);
  const layout = view.layout === 'focus' ? (view.ids.length > 1 ? 'dual' : 'single') : view.layout;
  const signature = `${layout}:${view.visibleLimit}:${view.visibleIds.join(',')}`;
  if (signature !== layoutSignature) {
    const focusedCamera = document.activeElement?.dataset.camera;
    stage.dataset.layout = layout;
    stage.dataset.capacity = String(view.visibleLimit);
    const order = [...view.ids, ...cameras.map(c => c.id).filter(id => !view.ids.includes(id))];
    for (const id of order) {
      const tile = tiles.get(id);
      tile.hidden = !view.visibleIds.includes(id);
      tile.classList.toggle('featured', view.ids.includes(id));
      stage.append(tile);
    }
    if (focusedCamera) {
      const focusTarget = !tiles.get(focusedCamera).hidden ? tiles.get(focusedCamera) : tiles.get(view.visibleIds[0]);
      focusTarget.focus({ preventScroll: true });
    }
    layoutSignature = signature;
  }
  for (const [id, tile] of tiles) {
    tile.classList.toggle('is-motion', view.motionIds.includes(id));
    const badge = tile.querySelector('.event-badge');
    const status = view.reason === 'pin' && view.ids.includes(id) ? 'PINNED'
      : view.motionIds.includes(id) ? 'ACTIVITY' : view.presenceIds.includes(id) ? 'HORSE LOCATION' : '';
    badge.textContent = status;
    badge.hidden = !status;
  }
  autoButton.setAttribute('aria-pressed', String(view.mode === 'auto'));
  gridButton.setAttribute('aria-pressed', String(view.mode === 'grid'));
  const labels = {
    overview: view.pendingPresence ? 'Waiting for a stable horse location…' : `Overview · ${view.visibleIds.length} of ${cameras.length} cameras`,
    motion: view.ids.length > 1 ? 'Activity · showing two active cameras' : 'Activity · camera enlarged',
    presence: 'Following horse locations',
    grid: `Grid · ${view.visibleIds.length} of ${cameras.length} cameras · switching paused`,
    pin: 'Pinned camera · press Esc to return to Auto',
  };
  if (description.textContent !== labels[view.reason]) description.textContent = labels[view.reason];
  countdown.textContent = view.reason === 'motion' ? `${Math.ceil(view.motionRemainingMs / 1000)}s` : '';
  document.querySelector('#clock').textContent = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(now);
}

autoButton.addEventListener('click', () => { controller.setMode('auto'); render(); });
gridButton.addEventListener('click', () => { controller.setMode('grid'); render(); });
profileSelect.addEventListener('change', () => { selectedProfile = profileSelect.value; applyProfile(); saveProfiles(); render(); });
tileCountSelect.addEventListener('change', () => { limits[selectedProfile] = Number(tileCountSelect.value); applyProfile(); saveProfiles(); render(); });
document.querySelector('#send-motion').addEventListener('click', () => { controller.motion(activityCameraSelect.value, Date.now()); render(); });
horseSelect.addEventListener('change', () => { zoneSelect.value = demoLocations.get(horseSelect.value) || 'unknown'; });
document.querySelector('#send-location').addEventListener('click', () => {
  const horse = horseSelect.value;
  const zone = zoneSelect.value;
  controller.location(horse, zone, Date.now());
  if (zone === 'unknown') demoLocations.delete(horse);
  else demoLocations.set(horse, zone);
  document.querySelector('#horse-status').textContent = demoLocations.size
    ? [...demoLocations].map(([id, area]) => `${id === 'horse-1' ? 'Horse 1' : 'Horse 2'} → ${area}`).join(' · ')
    : 'No horse locations set.';
  render();
});
document.querySelector('#clear-events').addEventListener('click', () => {
  controller.clearEvents();
  controller.setMode('auto');
  demoLocations.clear();
  zoneSelect.value = 'unknown';
  document.querySelector('#horse-status').textContent = 'No horse locations set.';
  render();
});

document.addEventListener('keydown', event => {
  if (event.key === 'Escape') { controller.setMode('auto'); render(); return; }
  if (!event.target.matches('.camera-tile') || !['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key)) return;
  event.preventDefault();
  const box = event.target.getBoundingClientRect();
  const center = { x: box.x + box.width / 2, y: box.y + box.height / 2 };
  const horizontal = ['ArrowLeft', 'ArrowRight'].includes(event.key);
  const sign = ['ArrowLeft', 'ArrowUp'].includes(event.key) ? -1 : 1;
  const options = [...tiles.values()].filter(tile => !tile.hidden && tile !== event.target).map(tile => {
    const rect = tile.getBoundingClientRect();
    const dx = rect.x + rect.width / 2 - center.x;
    const dy = rect.y + rect.height / 2 - center.y;
    return { tile, forward: sign * (horizontal ? dx : dy), across: Math.abs(horizontal ? dy : dx) };
  }).filter(option => option.forward > 1).sort((a, b) => (a.forward + a.across * 2) - (b.forward + b.across * 2));
  options[0]?.tile.focus();
});

render();
setInterval(render, 250);
