import React, { useRef, useState, useMemo } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { OrbitControls, Text, Trail, Float } from '@react-three/drei';
import * as THREE from 'three';
import type { WikipediaNode, WikipediaEdge } from '../types/graph';
import { FractalineLayoutEngine } from '../utils/fractalineLayout';

interface NodeProps {
  node: WikipediaNode;
  selected: boolean;
  onSelect: (nodeId: string) => void;
  animationPhase: number;
}

const Node: React.FC<NodeProps> = ({ node, selected, onSelect, animationPhase }) => {
  const meshRef = useRef<THREE.Mesh>(null);
  const [hovered, setHovered] = useState(false);

  useFrame((state) => {
    if (meshRef.current) {
      // Subtle rotation based on node level and animation phase
      const rotationSpeed = 0.1 * (1 / (node.level + 1));
      meshRef.current.rotation.y = state.clock.elapsedTime * rotationSpeed + animationPhase;
      
      // Breathing effect for selected nodes
      if (selected) {
        const breathe = 1 + Math.sin(state.clock.elapsedTime * 2) * 0.1;
        meshRef.current.scale.setScalar(breathe);
      }
    }
  });

  // Dynamic sizing based on level and connections
  const radius = Math.max(0.08, 0.25 - node.level * 0.04);
  
  // Color scheme based on categories and state
  const getNodeColor = () => {
    if (selected) return '#ff6b6b';
    if (hovered) return '#4ecdc4';
    
    // Color by category
    const primaryCategory = node.categories[0]?.toLowerCase() || '';
    if (primaryCategory.includes('mathematics')) return '#ffe66d';
    if (primaryCategory.includes('physics')) return '#ff8b94';
    if (primaryCategory.includes('computer')) return '#a8e6cf';
    return '#dda0dd';
  };

  // Depth-based opacity for 2.5D effect
  const opacity = Math.max(0.3, 1 - node.position.z * 0.1);

  return (
    <Float speed={1 + node.level * 0.5} rotationIntensity={0.1} floatIntensity={0.1}>
      <group position={[node.position.x, node.position.y, node.position.z]}>
        <Trail
          width={0.5}
          length={10}
          color={getNodeColor()}
          attenuation={(t) => t * t}
        >
          <mesh
            ref={meshRef}
            onClick={() => onSelect(node.id)}
            onPointerOver={() => setHovered(true)}
            onPointerOut={() => setHovered(false)}
          >
            <sphereGeometry args={[radius, 16, 16]} />
            <meshStandardMaterial 
              color={getNodeColor()} 
              transparent 
              opacity={opacity}
              metalness={0.3}
              roughness={0.4}
            />
          </mesh>
        </Trail>
        
        {/* Glow effect for important nodes */}
        {node.level === 0 && (
          <mesh>
            <sphereGeometry args={[radius * 1.5, 16, 16]} />
            <meshBasicMaterial 
              color={getNodeColor()} 
              transparent 
              opacity={0.1}
            />
          </mesh>
        )}
        
        {/* Labels */}
        {(selected || hovered) && (
          <Text
            position={[0, radius + 0.3, 0]}
            fontSize={0.15}
            color="white"
            anchorX="center"
            anchorY="middle"
            maxWidth={2}
            font="/fonts/Arial-Bold.ttf"
          >
            {node.title}
          </Text>
        )}
        
        {/* Category indicators */}
        {node.level > 0 && (
          <Text
            position={[0, -radius - 0.2, 0]}
            fontSize={0.08}
            color="#cccccc"
            anchorX="center"
            anchorY="middle"
            maxWidth={1.5}
          >
            {node.categories[0]}
          </Text>
        )}
      </group>
    </Float>
  );
};

interface EdgeProps {
  edge: WikipediaEdge;
  nodes: Map<string, WikipediaNode>;
}

const Edge: React.FC<EdgeProps> = ({ edge, nodes }) => {
  const lineRef = useRef<THREE.BufferGeometry>(null);
  const sourceId = typeof edge.source === 'string' ? edge.source : edge.source.id;
  const targetId = typeof edge.target === 'string' ? edge.target : edge.target.id;
  const sourceNode = nodes.get(sourceId);
  const targetNode = nodes.get(targetId);

  useFrame(() => {
    if (lineRef.current && sourceNode && targetNode) {
      // Could add edge animation here in the future
    }
  });

  if (!sourceNode || !targetNode) return null;

  const start = new THREE.Vector3(sourceNode.position.x, sourceNode.position.y, sourceNode.position.z);
  const end = new THREE.Vector3(targetNode.position.x, targetNode.position.y, targetNode.position.z);
  
  // Create curved edge for better visual appeal
  const midpoint = start.clone().add(end).multiplyScalar(0.5);
  const direction = end.clone().sub(start);
  const distance = direction.length();
  
  // Add curve based on edge weight and distance
  const curveHeight = distance * 0.1 * edge.weight;
  const perpendicular = new THREE.Vector3(-direction.y, direction.x, direction.z).normalize();
  midpoint.add(perpendicular.multiplyScalar(curveHeight));

  const curve = new THREE.QuadraticBezierCurve3(start, midpoint, end);
  const points = curve.getPoints(20);

  // Edge opacity based on weight and depth
  const avgDepth = (sourceNode.position.z + targetNode.position.z) * 0.5;
  const opacity = Math.max(0.1, edge.weight * 0.8 * (1 - avgDepth * 0.05));

  return (
    <line>
      <bufferGeometry ref={lineRef}>
        <bufferAttribute
          attach="attributes-position"
          args={[new Float32Array(points.flatMap(p => [p.x, p.y, p.z])), 3]}
        />
      </bufferGeometry>
      <lineBasicMaterial 
        color="#95a5a6" 
        transparent 
        opacity={opacity}
      />
    </line>
  );
};

interface GraphVisualizationProps {
  nodes: WikipediaNode[];
  edges: WikipediaEdge[];
}

export const GraphVisualization: React.FC<GraphVisualizationProps> = ({ nodes, edges }) => {
  const [selectedNode, setSelectedNode] = useState<string | null>(null);
  const layoutEngine = useRef(new FractalineLayoutEngine());

  // Apply fractaline layout
  const layoutNodes = useMemo(() => {
    const enhanced = layoutEngine.current.computeFractalineLayout(nodes, edges);
    return enhanced;
  }, [nodes, edges]);

  const nodeMap = new Map(layoutNodes.map(node => [node.id, node]));

  // Camera position based on graph extent
  const cameraPosition = useMemo(() => {
    const extent = Math.max(8, Math.sqrt(layoutNodes.length) * 2);
    return [extent, extent * 0.8, extent * 0.6] as [number, number, number];
  }, [layoutNodes.length]);

  return (
    <Canvas 
      camera={{ 
        position: cameraPosition, 
        fov: 60,
        near: 0.1,
        far: 1000
      }}
      style={{ background: 'radial-gradient(circle at center, #2a2a2a 0%, #1a1a1a 100%)' }}
    >
      {/* Lighting setup for depth perception */}
      <ambientLight intensity={0.4} />
      <pointLight position={[10, 10, 10]} intensity={1} color="#ffffff" />
      <pointLight position={[-10, -10, -10]} intensity={0.5} color="#4ecdc4" />
      <pointLight position={[0, 0, 20]} intensity={0.3} color="#ffe66d" />
      
      {/* Fog for depth cues */}
      <fog attach="fog" args={['#1a1a1a', 5, 50]} />
      
      {/* Render edges first (behind nodes) */}
      {edges.map((edge) => (
        <Edge 
          key={`${typeof edge.source === 'string' ? edge.source : edge.source.id}-${typeof edge.target === 'string' ? edge.target : edge.target.id}`} 
          edge={edge} 
          nodes={nodeMap} 
        />
      ))}
      
      {/* Render nodes */}
      {layoutNodes.map((node, index) => (
        <Node
          key={node.id}
          node={node}
          selected={selectedNode === node.id}
          onSelect={setSelectedNode}
          animationPhase={index * 0.2}
        />
      ))}
      
      {/* Grid for spatial reference */}
      <gridHelper 
        args={[20, 20, '#333333', '#222222']} 
        position={[0, -5, 0]}
      />
      
      <OrbitControls 
        enablePan={true} 
        enableZoom={true} 
        enableRotate={true}
        maxDistance={50}
        minDistance={2}
        autoRotate={false}
        dampingFactor={0.05}
        enableDamping={true}
      />
    </Canvas>
  );
};