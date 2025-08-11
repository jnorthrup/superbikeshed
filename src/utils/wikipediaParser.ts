import type { WikipediaGraph, WikipediaNode, WikipediaEdge } from '../types/graph';

export interface WikipediaParserConfig {
  maxNodes: number;
  maxEdges: number;
  includeCategories: string[];
  excludeCategories: string[];
  minTextLength: number;
}

export class WikipediaParser {
  constructor(config: Partial<WikipediaParserConfig> = {}) {
    // Configuration stored for future use
    const fullConfig = {
      maxNodes: 1000,
      maxEdges: 2000,
      includeCategories: [],
      excludeCategories: ['stub', 'disambiguation'],
      minTextLength: 50,
      ...config
    };
    console.log('WikipediaParser initialized with config:', fullConfig);
  }

  // Parse Wikipedia XML dump (simplified version for demo)
  public async parseWikipediaDump(): Promise<WikipediaGraph> {
    // This is a simplified parser - in a real implementation, 
    // you'd use a proper XML parser and handle the full Wikipedia dump format
    console.log('Parsing Wikipedia dump...');
    
    // For now, return enhanced sample data
    return this.createEnhancedSampleData();
  }

  // Create more comprehensive sample data
  public createEnhancedSampleData(): WikipediaGraph {
    const nodes: WikipediaNode[] = [
      // Mathematics cluster (level 0-2)
      {
        id: 'mathematics',
        title: 'Mathematics',
        text: 'Mathematics is an area of knowledge that includes the topics of numbers, formulas and related structures, shapes and the spaces in which they are contained, and quantities and their changes.',
        categories: ['Science', 'Mathematics', 'Formal sciences'],
        position: { x: 0, y: 0, z: 0 },
        level: 0
      },
      {
        id: 'algebra',
        title: 'Algebra',
        text: 'Algebra is one of the broad areas of mathematics. Roughly speaking, algebra is the study of mathematical symbols and the rules for manipulating these symbols in formulas.',
        categories: ['Mathematics', 'Algebra'],
        position: { x: 2, y: 1, z: 0.5 },
        level: 1
      },
      {
        id: 'geometry',
        title: 'Geometry',
        text: 'Geometry is, with arithmetic, one of the oldest branches of mathematics. It is concerned with properties of space such as the distance, shape, size, and relative position of figures.',
        categories: ['Mathematics', 'Geometry'],
        position: { x: -2, y: 1, z: 0.5 },
        level: 1
      },
      {
        id: 'calculus',
        title: 'Calculus',
        text: 'Calculus is the mathematical study of continuous change, in the same way that geometry is the study of shape, and algebra is the study of generalizations of arithmetic operations.',
        categories: ['Mathematics', 'Mathematical analysis', 'Calculus'],
        position: { x: 0, y: 2, z: 1 },
        level: 1
      },
      {
        id: 'linear_algebra',
        title: 'Linear Algebra',
        text: 'Linear algebra is the branch of mathematics concerning linear equations such as linear maps and their representations in vector spaces and through matrices.',
        categories: ['Mathematics', 'Algebra', 'Linear algebra'],
        position: { x: 3, y: 2, z: 1 },
        level: 2
      },
      {
        id: 'topology',
        title: 'Topology',
        text: 'Topology is the study of geometrical properties and spatial relations unaffected by the continuous change of shape or size of figures.',
        categories: ['Mathematics', 'Topology'],
        position: { x: -1, y: 3, z: 1.5 },
        level: 2
      },
      
      // Physics cluster (level 0-2)
      {
        id: 'physics',
        title: 'Physics',
        text: 'Physics is the natural science that studies matter, its fundamental constituents, its motion and behavior through space and time, and the related entities of energy and force.',
        categories: ['Science', 'Physics', 'Natural sciences'],
        position: { x: 4, y: 0, z: 0 },
        level: 0
      },
      {
        id: 'quantum_mechanics',
        title: 'Quantum Mechanics',
        text: 'Quantum mechanics is a fundamental theory in physics that provides a description of the physical properties of nature at the scale of atoms and subatomic particles.',
        categories: ['Physics', 'Quantum mechanics'],
        position: { x: 5, y: 1, z: 0.5 },
        level: 1
      },
      {
        id: 'relativity',
        title: 'Theory of Relativity',
        text: 'The theory of relativity usually encompasses two interrelated theories by Albert Einstein: special relativity and general relativity.',
        categories: ['Physics', 'Relativity'],
        position: { x: 3, y: 1, z: 0.5 },
        level: 1
      },
      
      // Computer Science cluster (level 0-2)
      {
        id: 'computer_science',
        title: 'Computer Science',
        text: 'Computer science is the study of algorithmic processes, computational systems and the design of computer systems and their applications.',
        categories: ['Science', 'Computer science', 'Technology'],
        position: { x: -4, y: 0, z: 0 },
        level: 0
      },
      {
        id: 'algorithms',
        title: 'Algorithm',
        text: 'An algorithm is a finite sequence of rigorous instructions, typically used to solve a class of specific problems or to perform a computation.',
        categories: ['Computer science', 'Algorithms'],
        position: { x: -5, y: 1, z: 0.5 },
        level: 1
      },
      {
        id: 'machine_learning',
        title: 'Machine Learning',
        text: 'Machine learning is a method of data analysis that automates analytical model building. It is a branch of artificial intelligence.',
        categories: ['Computer science', 'Machine learning', 'Artificial intelligence'],
        position: { x: -3, y: 1, z: 0.5 },
        level: 1
      },
      
      // Cross-disciplinary connections (level 2-3)
      {
        id: 'mathematical_physics',
        title: 'Mathematical Physics',
        text: 'Mathematical physics refers to the development of mathematical methods for application to problems in physics.',
        categories: ['Mathematics', 'Physics', 'Mathematical physics'],
        position: { x: 2, y: 0, z: 2 },
        level: 2
      },
      {
        id: 'computational_mathematics',
        title: 'Computational Mathematics',
        text: 'Computational mathematics involves mathematical research in mathematics as well as in areas of science where computation plays a central and essential role.',
        categories: ['Mathematics', 'Computer science', 'Computational mathematics'],
        position: { x: -2, y: 0, z: 2 },
        level: 2
      },
      {
        id: 'quantum_computing',
        title: 'Quantum Computing',
        text: 'Quantum computing is a type of computation whose operations can harness the phenomena of quantum mechanics, such as superposition, interference, and entanglement.',
        categories: ['Physics', 'Computer science', 'Quantum computing'],
        position: { x: 1, y: -1, z: 2.5 },
        level: 3
      }
    ];

    const edges: WikipediaEdge[] = [
      // Mathematics connections
      { source: 'mathematics', target: 'algebra', weight: 1.0 },
      { source: 'mathematics', target: 'geometry', weight: 1.0 },
      { source: 'mathematics', target: 'calculus', weight: 1.0 },
      { source: 'algebra', target: 'linear_algebra', weight: 0.9 },
      { source: 'geometry', target: 'topology', weight: 0.7 },
      { source: 'calculus', target: 'topology', weight: 0.5 },
      
      // Physics connections
      { source: 'physics', target: 'quantum_mechanics', weight: 1.0 },
      { source: 'physics', target: 'relativity', weight: 1.0 },
      
      // Computer Science connections
      { source: 'computer_science', target: 'algorithms', weight: 1.0 },
      { source: 'computer_science', target: 'machine_learning', weight: 0.8 },
      
      // Cross-disciplinary connections
      { source: 'mathematics', target: 'mathematical_physics', weight: 0.8 },
      { source: 'physics', target: 'mathematical_physics', weight: 0.8 },
      { source: 'mathematics', target: 'computational_mathematics', weight: 0.7 },
      { source: 'computer_science', target: 'computational_mathematics', weight: 0.7 },
      { source: 'physics', target: 'quantum_computing', weight: 0.6 },
      { source: 'computer_science', target: 'quantum_computing', weight: 0.9 },
      { source: 'quantum_mechanics', target: 'quantum_computing', weight: 0.8 },
      { source: 'algorithms', target: 'machine_learning', weight: 0.6 },
      
      // Additional semantic connections
      { source: 'linear_algebra', target: 'machine_learning', weight: 0.5 },
      { source: 'calculus', target: 'machine_learning', weight: 0.4 },
      { source: 'linear_algebra', target: 'quantum_mechanics', weight: 0.3 },
      { source: 'topology', target: 'quantum_mechanics', weight: 0.2 }
    ];

    return { nodes, edges };
  }

  // Future utility methods for text parsing and similarity calculation
  // These methods are kept for potential future enhancements
  
  /*
  // Parse links from Wikipedia text
  private extractLinksFromText(text: string): string[] {
    // Simple regex to find [[link]] patterns
    const linkRegex = /\[\[([^\]|]+)(?:\|[^\]]+)?\]\]/g;
    const links: string[] = [];
    let match;
    
    while ((match = linkRegex.exec(text)) !== null) {
      links.push(match[1].replace(/\s+/g, '_').toLowerCase());
    }
    
    return links;
  }

  // Calculate semantic similarity between articles
  private calculateSimilarity(node1: WikipediaNode, node2: WikipediaNode): number {
    const categories1 = new Set(node1.categories);
    const categories2 = new Set(node2.categories);
    const intersection = new Set([...categories1].filter(x => categories2.has(x)));
    const union = new Set([...categories1, ...categories2]);
    
    return intersection.size / union.size; // Jaccard similarity
  }
  */
}