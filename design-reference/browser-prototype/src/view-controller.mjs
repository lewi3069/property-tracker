/** Pure view selection. The prototype feeds simulated, already-normalized events. */
export class ViewController {
  constructor({ cameras, zoneCameras = {}, motionHoldMs = 30000,
    presenceStableMs = 5000, presenceStaleMs = 60000, minimumDwellMs = 8000, visibleLimit = 4 }) {
    if (!cameras.length || new Set(cameras).size !== cameras.length) {
      throw new Error('Camera IDs must be non-empty and unique');
    }
    this.cameras = [...cameras];
    this.zoneCameras = zoneCameras;
    this.motionHoldMs = motionHoldMs;
    this.presenceStableMs = presenceStableMs;
    this.presenceStaleMs = presenceStaleMs;
    this.minimumDwellMs = minimumDwellMs;
    this.motions = new Map();
    this.horses = new Map();
    this.mode = 'auto';
    this.pinned = null;
    this.current = null;
    this.changedAt = 0;
    if (!this.setVisibleLimit(visibleLimit)) throw new Error('Visible limit must be a positive integer');
  }

  setVisibleLimit(limit) {
    if (!Number.isSafeInteger(limit) || limit < 1) return false;
    this.visibleLimit = Math.min(limit, this.cameras.length);
    this.current = null;
    return true;
  }

  motion(cameraId, now) {
    if (!this.cameras.includes(cameraId)) return false;
    this.motions.set(cameraId, { triggeredAt: now, expiresAt: now + this.motionHoldMs });
    return true;
  }

  location(horseId, zone, now) {
    if (typeof horseId !== 'string' || !horseId.trim()) return false;
    if (!Object.hasOwn(this.zoneCameras, zone)) {
      this.horses.delete(horseId); // Unknown/lost is not a new location.
      return false;
    }
    const previous = this.horses.get(horseId);
    this.horses.set(horseId, {
      zone, since: previous?.zone === zone ? previous.since : now, updatedAt: now,
    });
    return true;
  }

  setMode(mode, cameraId = null) {
    if (!['auto', 'grid', 'pin'].includes(mode)) return false;
    if (mode === 'pin' && !this.cameras.includes(cameraId)) return false;
    this.mode = mode;
    this.pinned = mode === 'pin' ? cameraId : null;
    this.current = null;
    return true;
  }

  clearEvents() {
    this.motions.clear();
    this.horses.clear();
    this.current = null;
  }

  snapshot(now) {
    for (const [id, event] of this.motions) if (event.expiresAt <= now) this.motions.delete(id);
    for (const [id, horse] of this.horses) {
      if (now - horse.updatedAt >= this.presenceStaleMs) this.horses.delete(id);
    }
    const motionIds = [...this.motions]
      .sort((a, b) => b[1].triggeredAt - a[1].triggeredAt || a[0].localeCompare(b[0]))
      .map(([id]) => id);
    const stableHorses = [...this.horses]
      .filter(([, horse]) => now - horse.since >= this.presenceStableMs)
      .sort(([a], [b]) => a.localeCompare(b));
    const presenceIds = [...new Set(stableHorses.flatMap(([, horse]) => this.zoneCameras[horse.zone]))]
      .filter(id => this.cameras.includes(id));
    let candidate;
    if (this.mode === 'pin') candidate = { layout: 'full', ids: [this.pinned], reason: 'pin', priority: 100 };
    else if (this.mode === 'grid') candidate = { layout: 'grid', ids: [], reason: 'grid', priority: 100 };
    else if (motionIds.length) candidate = { layout: 'focus', ids: motionIds.slice(0, Math.min(2, this.visibleLimit)), reason: 'motion', priority: 20 };
    else if (presenceIds.length) candidate = { layout: 'focus', ids: presenceIds.slice(0, Math.min(2, this.visibleLimit)), reason: 'presence', priority: 10 };
    else candidate = { layout: 'grid', ids: [], reason: 'overview', priority: 0 };

    const current = this.current;
    const eligibleIds = current?.reason === 'motion' ? motionIds : presenceIds;
    const currentStillEligible = current?.ids.length && current.ids.every(id => eligibleIds.includes(id));
    const sameView = current && current.reason === candidate.reason && current.ids.join('|') === candidate.ids.join('|');
    // Higher-priority activity interrupts at once; equal-priority changes wait briefly.
    const hold = current && !sameView && currentStillEligible && candidate.priority <= current.priority
      && now - this.changedAt < this.minimumDwellMs;
    if (!hold && !sameView) {
      this.current = candidate;
      this.changedAt = now;
    }
    const view = this.current;
    const visibleIds = view.layout === 'full' ? [...view.ids]
      : [...view.ids, ...this.cameras.filter(id => !view.ids.includes(id))].slice(0, this.visibleLimit);
    return {
      ...view, ids: [...view.ids], mode: this.mode, motionIds, presenceIds,
      visibleIds, visibleLimit: this.visibleLimit,
      motionRemainingMs: Math.max(0, ...view.ids.map(id => (this.motions.get(id)?.expiresAt ?? now) - now)),
      pendingPresence: [...this.horses.values()].some(horse => now - horse.since < this.presenceStableMs),
      stableHorses: stableHorses.map(([id, horse]) => ({ id, zone: horse.zone })),
    };
  }
}
