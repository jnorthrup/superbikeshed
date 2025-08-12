import React, { useRef, useState, useMemo, useEffect } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { OrbitControls, Text, Trail } from '@react-three/drei';
import * as THREE from 'three';
import type { WikipediaNode, WikipediaEdge } from '../types/graph';
import { FractalineLayoutEngine } from '../utils/fractalineLayout';

interface NodeProps {
  node: WikipediaNode;
  selected: boolean;
  onSelect: (nodeId:string) => void;
}

const Node: React.FC<NodeProps> = ({ node, selected, onSelect }) => {
  const meshRef = useRef<THREE.Mesh>(null);
  const [hovered, setHovered] = useState(false);

  useFrame((state) => {
    if (meshRef.current) {
      if (node.position) {
        meshRef.current.position.lerp(new THREE.Vector3(node.position.x, node.position.y, node.position.z), 0.1);
      }
      if (selected) {
        const scale = 1 + Math.sin(state.clock.elapsedTime * 2) * 0.1;
        meshRef.current.scale.set(scale, scale, scale);
      } else {
        meshRef.current.scale.lerp(new THREE.Vector3(1, 1, 1), 0.1);
      }
    }
  });

  const radius = Math.max(0.1, 0.3 - (node.level || 0) * 0.05);
  
  const getNodeColor = () => {
    if (selected) return '#ff6b6b';
    if (hovered) return '#4ecdc4';
    
    switch (node.domain) {
      case 'Mathematics': return '#ffe66d';
      case 'Physics': return '#ff8b94';
      case 'Computer Science': return '#a8e6cf';
      default: return '#dda0dd';
    }
  };

  const opacity = node.position ? Math.max(0.3, 1 - (node.position.z || 0) * 0.1) : 1;

  return (
    <group position={node.position ? [node.position.x, node.position.y, node.position.z] : [0, 0, 0]}>
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
        
        {(selected || hovered) && (
          <Text
            position={[0, -radius - 0.2, 0]}
            fontSize={0.08}
            color="#cccccc"
            anchorX="center"
            anchorY="middle"
            maxWidth={1.5}
          >
            {node.domain}
          </Text>
        )}
      </group>
  );
};

interface EdgeProps {
  edge: WikipediaEdge;
  nodes: Map<string, WikipediaNode>;
}

const Edge: React.FC<EdgeProps> = ({ edge, nodes }) => {
  const sourceId = typeof edge.source === 'string' ? edge.source : edge.source.id;
  const targetId = typeof edge.target === 'string' ? edge.target : edge.target.id;
  const sourceNode = nodes.get(sourceId);
  const targetNode = nodes.get(targetId);

  if (!sourceNode || !targetNode || !sourceNode.position || !targetNode.position) return null;

  const start = new THREE.Vector3(sourceNode.position.x, sourceNode.position.y, sourceNode.position.z);
  const end = new THREE.Vector3(targetNode.position.x, targetNode.position.y, targetNode.position.z);
  
  const mid = new THREE.Vector3().addVectors(start, end).multiplyScalar(0.5);
  mid.z += start.distanceTo(end) * 0.2;

  const curve = new THREE.QuadraticBezierCurve3(start, mid, end);
  const points = curve.getPoints(20);

  return (
    <line>
      <bufferGeometry attach="geometry" setFromPoints={points} />
      <lineBasicMaterial color="#555" transparent opacity={0.3} />
    </line>
  );
};


interface GraphVisualizationProps {
  nodes: WikipediaNode[];
  edges: WikipediaEdge[];
}

export const GraphVisualization: React.FC<GraphVisualizationProps> = ({ nodes, edges }) => {
  const [selectedNode, setSelectedNode] = useState<string | null>(null);
  const [layoutNodes, setLayoutNodes] = useState<WikipediaNode[]>([]);
  const layoutEngine = useRef(new FractalineLayoutEngine());

  useEffect(() => {
    const engine = layoutEngine.current;
    const newLayoutNodes = engine.computeFractalineLayout(nodes, edges);
    setLayoutNodes(newLayoutNodes);

    return () => {
      engine.stop();
    };
  }, [nodes, edges]);

  const nodeMap = useMemo(() => new Map(layoutNodes.map(node => [node.id, node])), [layoutNodes]);

  const cameraPosition = useMemo(() => {
    const extent = Math.max(10, Math.sqrt(nodes.length) * 3);
    return [extent, extent, extent] as [number, number, number];
  }, [nodes.length]);

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
      {layoutNodes.map((node) => (
        <Node
          key={node.id}
          node={node}
          selected={selectedNode === node.id}
          onSelect={setSelectedNode}
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