// Core game types
export interface Vector2D {
    x: number;
    y: number;
}

export interface GameContext {
    entityManager: EntityManager;
    camera: Camera;
    time: number;
    deltaTime: number;
}

export interface EntityManager {
    units: Unit[];
    buildings: Building[];
    effects: Effect[];
    captions: Caption[];
    addEffect(effect: Effect): void;
    addCaption(caption: Caption): void;
}

export interface Unit {
    id?: string;
    team: number;
    type: UnitType;
    x: number;
    y: number;
    hp: number;
    maxHp: number;
    veterancyLevel: VeterancyLevel;
    effectiveAuthority: number;
    currentCommander?: Unit;
    provideMoraleBonus: boolean;
    canPromoteSubordinates: boolean;
    getDistance(target: Unit): number;
    calculateEffectiveAuthority(): number;
    applyMoraleBonus(gameContext: GameContext): void;
    handleSubordinatePromotions(gameContext: GameContext): void;
}

export interface UnitType {
    name: string;
    displayName: string;
    cost: Record<string, number>;
    maxHp: number;
    maxSpeed: number;
    computroniumCoreLevel: number;
}

export type VeterancyLevel = 'GREEN' | 'REGULAR' | 'VETERAN' | 'ELITE' | 'HERO';

export interface Building {
    id?: string;
    team: number;
    type: BuildingType;
    x: number;
    y: number;
    hp: number;
    maxHp: number;
}

export interface BuildingType {
    name: string;
    displayName: string;
    cost: Record<string, number>;
    maxHp: number;
}

export interface Effect {
    x: number;
    y: number;
    type: string;
    duration: number;
}

export interface Caption {
    x: number;
    y: number;
    text: string;
    color: string;
    duration: number;
}

export interface Camera {
    x: number;
    y: number;
    zoom: number;
    canvasWidth: number;
    canvasHeight: number;
} 