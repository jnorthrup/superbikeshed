# RTS Game

A real-time strategy game built with TypeScript, Three.js, and modern web technologies.

## Development

```bash
# Install dependencies
npm install

# Start development server
npm start

# Run type checking
npm run type-check

# Run linting
npm run lint

# Build for production
npm run build
```

## GitHub Pages Deployment

The game is automatically deployed to GitHub Pages when changes are pushed to the main branch. The deployment process:

1. Runs type checking
2. Runs linting
3. Builds the project with production settings
4. Deploys to the `gh-pages` branch

The game will be available at: https://yourusername.github.io/superbikeshed/rtsgame/

## Project Structure

```
rtsgame/
├── src/              # Source files
│   ├── core/        # Core game systems
│   ├── entities/    # Game entities
│   ├── systems/     # Game systems
│   └── types/       # TypeScript type definitions
├── public/          # Static assets
├── docs/            # Documentation
└── tests/           # Test files
```

## Features

- Real-time strategy gameplay
- Advanced command hierarchy system
- Resource management
- Unit veterancy and progression
- Modern web technologies
- TypeScript for type safety
- Three.js for 3D graphics

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

MIT 