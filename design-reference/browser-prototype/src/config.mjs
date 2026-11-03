// Illustrative cameras only. No addresses, credentials, or live entities.
export const cameras = [
  { id: 'barn', name: 'Barn & paddock', area: 'BARN', scene: 'barn', number: '01' },
  { id: 'yard', name: 'Back yard', area: 'YARD', scene: 'yard', number: '02' },
  { id: 'pasture', name: 'West pasture', area: 'PASTURE', scene: 'pasture', number: '03' },
  { id: 'gate', name: 'Driveway & gate', area: 'GATE', scene: 'gate', number: '04' },
  { id: 'barn-entry', name: 'Barn entrance', area: 'BARN ENTRY', scene: 'barn', number: '05' },
  { id: 'east-pasture', name: 'East pasture', area: 'EAST PASTURE', scene: 'pasture', number: '06' },
  { id: 'water', name: 'Water trough', area: 'WATER', scene: 'pasture', number: '07' },
  { id: 'arena', name: 'Riding arena', area: 'ARENA', scene: 'yard', number: '08' },
  { id: 'house', name: 'House approach', area: 'HOUSE', scene: 'gate', number: '09' },
];

export const zoneCameras = {
  barn: ['barn'],
  yard: ['yard'],
  pasture: ['pasture'],
  gate: ['gate'],
};

// Sample screen preferences, not measured decoder limits for a device model.
export const displayProfiles = [
  { id: 'office', name: 'Office TV', visibleLimit: 4 },
  { id: 'large', name: 'Large TV', visibleLimit: 9 },
  { id: 'compact', name: 'Compact screen', visibleLimit: 1 },
];
export const tileCounts = [1, 2, 4, 6, 9];
