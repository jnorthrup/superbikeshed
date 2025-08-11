import React, { useRef, useState } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { OrbitControls, Text } from '@react-three/drei';
import * as THREE from 'three';
import type { WikipediaNode, WikipediaEdge } from '../types/graph';

interface NodeProps {
  node: WikipediaNode;
  selected: boolean;
  onSelect: (nodeId: string) => void;
}

const Node: React.FC<NodeProps> = ({ node, selected, onSelect }) => {
  const meshRef = useRef<THREE.Mesh>(null);
  const [hovered, setHovered] = useState(false);

  useFrame((state) => {
    if (meshRef.current) {
      // Subtle animation based on node level
      meshRef.current.rotation.y = state.clock.elapsedTime * 0.1 * (1 / (node.level + 1));
    }
  });

  const radius = Math.max(0.1, 0.3 - node.level * 0.05);
  const color = selected ? '#ff6b6b' : hovered ? '#4ecdc4' : '#ffe66d';

  return (
    <group position={[node.position.x, node.position.y, node.position.z]}>
      <mesh
        ref={meshRef}
        onClick={() => onSelect(node.id)}
        onPointerOver={() => setHovered(true)}
        onPointerOut={() => setHovered(false)}
      >
        <sphereGeometry args={[radius, 16, 16]} />
        <meshStandardMaterial color={color} />
      </mesh>
      {(selected || hovered) && (
        <Text
          position={[0, radius + 0.3, 0]}
          fontSize={0.2}
          color="white"
          anchorX="center"
          anchorY="middle"
        >
          {node.title}
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
  const sourceNode = nodes.get(edge.source);
  const targetNode = nodes.get(edge.target);

  if (!sourceNode || !targetNode) return null;

  const start = new THREE.Vector3(sourceNode.position.x, sourceNode.position.y, sourceNode.position.z);
  const end = new THREE.Vector3(targetNode.position.x, targetNode.position.y, targetNode.position.z);
  const direction = end.clone().sub(start);
  const length = direction.length();

  return (
    <group>
      <mesh position={start.clone().add(direction.clone().multiplyScalar(0.5))}>
        <cylinderGeometry args={[0.01, 0.01, length, 8]} />
        <meshBasicMaterial color="#95a5a6" opacity={edge.weight * 0.7} transparent />
      </mesh>
    </group>
  );
};

interface GraphVisualizationProps {
  nodes: WikipediaNode[];
  edges: WikipediaEdge[];
}

export const GraphVisualization: React.FC<GraphVisualizationProps> = ({ nodes, edges }) => {
  const [selectedNode, setSelectedNode] = useState<string | null>(null);
  const nodeMap = new Map(nodes.map(node => [node.id, node]));

  return (
    <Canvas camera={{ position: [5, 5, 5], fov: 60 }}>
      <ambientLight intensity={0.6} />
      <pointLight position={[10, 10, 10]} intensity={1} />
      <pointLight position={[-10, -10, -10]} intensity={0.5} />
      
      {/* Render edges first (behind nodes) */}
      {edges.map((edge, index) => (
        <Edge key={index} edge={edge} nodes={nodeMap} />
      ))}
      
      {/* Render nodes */}
      {nodes.map((node) => (
        <Node
          key={node.id}
          node={node}
          selected={selectedNode === node.id}
          onSelect={setSelectedNode}
        />
      ))}
      
      <OrbitControls enablePan={true} enableZoom={true} enableRotate={true} />
    </Canvas>
  );
};