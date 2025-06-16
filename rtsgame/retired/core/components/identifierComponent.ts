export class IdentifierComponent {
    constructor(id, typeName, team) {
        this.id = id;
        this.typeName = typeName;
        this.team = team;
    }

    static create(id, typeName, team) {
        return new IdentifierComponent(id, typeName, team);
    }

    toJSON() {
        return {
            id: this.id,
            typeName: this.typeName,
            team: this.team
        };
    }

    static fromJSON(data) {
        return new IdentifierComponent(data.id, data.typeName, data.team);
    }
} 