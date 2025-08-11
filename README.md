# 2.5D Fractaline Wikipedia Graph Visualization

A cutting-edge interactive visualization of Wikipedia data using fractaline layout algorithms and 2.5D rendering techniques, built entirely in JavaScript/TypeScript.

![Wikipedia Graph Visualization](https://github.com/jnorthrup/superbikeshed/workflows/Deploy%20to%20GitHub%20Pages/badge.svg)

## ✨ Features

- **Fractaline Recursive Layout**: Hierarchical spiral-based positioning with depth spacing
- **2.5D Depth Cues**: Fog effects, opacity gradients, and z-axis positioning for spatial perception
- **Semantic Clustering**: Knowledge domains color-coded by category (Mathematics, Physics, Computer Science)
- **Dynamic Animations**: Floating nodes, breathing effects, and subtle rotations
- **Interactive Controls**: Click to select nodes, orbit controls for navigation
- **Cross-disciplinary Mapping**: Visualizes connections between different fields of knowledge

## 🚀 Live Demo

Visit the live demonstration: [https://jnorthrup.github.io/superbikeshed/](https://jnorthrup.github.io/superbikeshed/)

## 🛠 Technology Stack

- **React + TypeScript**: Modern component-based architecture
- **Three.js + React Three Fiber**: 3D rendering and WebGL graphics
- **React Three Drei**: Advanced 3D components and helpers
- **D3 Force Simulation**: Physics-based layout calculations
- **Vite**: Fast development and optimized builds

## 📊 Data Sources

The visualization supports multiple data modes:
- **Sample Data**: 6-node demonstration with core mathematical concepts
- **Enhanced Data**: 15-node comprehensive graph spanning Mathematics, Physics, and Computer Science

## 🏗 Architecture

### Core Components

1. **GraphVisualization**: Main 3D rendering component with fractaline layout
2. **WikipediaParser**: Data processing and graph generation utilities
3. **FractalineLayoutEngine**: Recursive positioning algorithms with physics simulation
4. **Interactive UI**: Data source selection and feature controls

### Fractaline Algorithm

The fractaline layout combines:
- Spiral-based hierarchical positioning
- Physics simulation for natural node placement
- Depth-based z-axis positioning for 2.5D effect
- Semantic clustering by knowledge domain

## 🎮 Usage

### Local Development

```bash
# Clone the repository
git clone https://github.com/jnorthrup/superbikeshed.git
cd superbikeshed

# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build
```

### Interactive Controls

- **Mouse Drag**: Rotate the 3D view
- **Mouse Wheel**: Zoom in/out
- **Click Nodes**: Select and highlight nodes
- **Radio Buttons**: Switch between data sources

## 📈 Performance

- Optimized for 100+ nodes with smooth 60fps rendering
- WebGL-accelerated graphics with efficient memory usage
- Responsive design supporting various screen sizes

## 🔮 Future Enhancements

- Real-time Wikipedia API integration
- Advanced filtering and search capabilities
- VR/AR support for immersive exploration
- Machine learning-based similarity clustering
- Export capabilities for research and education

## 📝 License

MIT License - see [LICENSE](LICENSE) for details.

## 🤝 Contributing

Contributions welcome! Please read our contributing guidelines and submit pull requests for any improvements.

---

*Built with ❤️ for the Wikipedia community and knowledge visualization enthusiasts.*
