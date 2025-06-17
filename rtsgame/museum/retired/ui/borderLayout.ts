// ####################################################################################################
// #                                                                                                  #
// #   DEPRECATED FILE: This BorderLayout system is deprecated and scheduled for deletion.            #
// #   The project is transitioning to React for its UI framework (see js/ui/components/UiRoot.jsx).  #
// #   Future UI development should use React components for layout and UI elements.                  #
// #                                                                                                  #
// ####################################################################################################

// js/ui/borderLayout.js
// Recursive Border Layout System inspired by AWT BorderLayout

// Simple UUID generator (replaces the 'uuid' npm package for browser compatibility)
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

export const BorderRegion = {
    NORTH: 'north',
    SOUTH: 'south',
    EAST: 'east',
    WEST: 'west',
    CENTER: 'center'
};

export class BorderLayoutContainer {
    constructor(x, y, width, height, options = {}) {
        this.id = generateUUID();  // Assign a unique UUID to each window instance
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        
        // Layout configuration
        this.padding = options.padding || 5;
        this.borderThickness = options.borderThickness || 1;
        this.backgroundColor = options.backgroundColor || 'rgba(30, 30, 40, 0.9)';
        this.borderColor = options.borderColor || '#666666';
        this.title = options.title || '';
        this.titleHeight = options.titleHeight || 20;
        
        // Region components
        this.regions = new Map();
        this.regionSizes = new Map();
        
        // Default region sizes (can be overridden)
        this.regionSizes.set(BorderRegion.NORTH, 100);
        this.regionSizes.set(BorderRegion.SOUTH, 80);
        this.regionSizes.set(BorderRegion.EAST, 150);
        this.regionSizes.set(BorderRegion.WEST, 150);
        
        // State
        this.visible = true;
        this.minimized = false;
        this.resizable = options.resizable !== false;
        this.movable = options.movable !== false;
        this.closable = options.closable !== false;
        
        // Event handling
        this.isDragging = false;
        this.isResizing = false;
        this.dragStartX = 0;
        this.dragStartY = 0;
        this.resizeHandle = null;
        
        // Child windows
        this.children = [];
        this.parent = null;
        
        // Focus management
        this.focused = false;
        this.zIndex = 1;
        
        // Calculate actual content area
        this.updateLayout();
    }
    
    updateLayout() {
        // Title bar area
        const titleBarHeight = this.title ? this.titleHeight : 0;
        
        // Available content area after title and padding
        const contentX = this.x + this.padding + this.borderThickness;
        const contentY = this.y + titleBarHeight + this.padding + this.borderThickness;
        const contentWidth = this.width - (this.padding + this.borderThickness) * 2;
        const contentHeight = this.height - titleBarHeight - (this.padding + this.borderThickness) * 2;
        
        // Calculate region bounds
        this.regionBounds = new Map();
        
        // North region
        if (this.regions.has(BorderRegion.NORTH)) {
            const northHeight = Math.min(this.regionSizes.get(BorderRegion.NORTH), contentHeight / 3);
            this.regionBounds.set(BorderRegion.NORTH, {
                x: contentX,
                y: contentY,
                width: contentWidth,
                height: northHeight
            });
        }
        
        // South region
        if (this.regions.has(BorderRegion.SOUTH)) {
            const southHeight = Math.min(this.regionSizes.get(BorderRegion.SOUTH), contentHeight / 3);
            this.regionBounds.set(BorderRegion.SOUTH, {
                x: contentX,
                y: contentY + contentHeight - southHeight,
                width: contentWidth,
                height: southHeight
            });
        }
        
        // Calculate remaining vertical space for center
        const northHeight = this.regionBounds.has(BorderRegion.NORTH) ? 
            this.regionBounds.get(BorderRegion.NORTH).height : 0;
        const southHeight = this.regionBounds.has(BorderRegion.SOUTH) ? 
            this.regionBounds.get(BorderRegion.SOUTH).height : 0;
        const middleY = contentY + northHeight;
        const middleHeight = contentHeight - northHeight - southHeight;
        
        // West region
        if (this.regions.has(BorderRegion.WEST)) {
            const westWidth = Math.min(this.regionSizes.get(BorderRegion.WEST), contentWidth / 3);
            this.regionBounds.set(BorderRegion.WEST, {
                x: contentX,
                y: middleY,
                width: westWidth,
                height: middleHeight
            });
        }
        
        // East region
        if (this.regions.has(BorderRegion.EAST)) {
            const eastWidth = Math.min(this.regionSizes.get(BorderRegion.EAST), contentWidth / 3);
            this.regionBounds.set(BorderRegion.EAST, {
                x: contentX + contentWidth - eastWidth,
                y: middleY,
                width: eastWidth,
                height: middleHeight
            });
        }
        
        // Center region (remaining space)
        const westWidth = this.regionBounds.has(BorderRegion.WEST) ? 
            this.regionBounds.get(BorderRegion.WEST).width : 0;
        const eastWidth = this.regionBounds.has(BorderRegion.EAST) ? 
            this.regionBounds.get(BorderRegion.EAST).width : 0;
        
        if (this.regions.has(BorderRegion.CENTER)) {
            this.regionBounds.set(BorderRegion.CENTER, {
                x: contentX + westWidth,
                y: middleY,
                width: contentWidth - westWidth - eastWidth,
                height: middleHeight
            });
        }
        
        // Update child layouts
        this.regions.forEach((component, region) => {
            const bounds = this.regionBounds.get(region);
            if (bounds && component.updateLayout) {
                component.setSize(bounds.width, bounds.height);
                component.setPosition(bounds.x, bounds.y);
                component.updateLayout();
            }
        });
    }
    
    addComponent(component, region) {
        if (!Object.values(BorderRegion).includes(region)) {
            throw new Error(`Invalid border region: ${region}`);
        }
        
        this.regions.set(region, component);
        
        // Set parent relationship for nested containers
        if (component instanceof BorderLayoutContainer) {
            component.parent = this;
            this.children.push(component);
        }
        
        this.updateLayout();
        return this;
    }
    
    removeComponent(region) {
        const component = this.regions.get(region);
        if (component) {
            this.regions.delete(region);
            
            // Remove parent relationship
            if (component instanceof BorderLayoutContainer) {
                component.parent = null;
                this.children = this.children.filter(child => child !== component);
            }
            
            this.updateLayout();
        }
        return component;
    }
    
    setRegionSize(region, size) {
        this.regionSizes.set(region, size);
        this.updateLayout();
        return this;
    }
    
    setPosition(x, y) {
        this.x = x;
        this.y = y;
        this.updateLayout();
        return this;
    }
    
    setSize(width, height) {
        this.width = width;
        this.height = height;
        this.updateLayout();
        return this;
    }
    
    setBounds(x, y, width, height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.updateLayout();
        return this;
    }
    
    contains(x, y) {
        return x >= this.x && x <= this.x + this.width &&
               y >= this.y && y <= this.y + this.height;
    }
    
    getTitleBarBounds() {
        if (!this.title) return null;
        return {
            x: this.x,
            y: this.y,
            width: this.width,
            height: this.titleHeight
        };
    }
    
    getResizeHandles() {
        if (!this.resizable) return [];
        
        const handleSize = 8;
        return [
            { type: 'se', x: this.x + this.width - handleSize, y: this.y + this.height - handleSize, 
              width: handleSize, height: handleSize },
            { type: 'e', x: this.x + this.width - handleSize/2, y: this.y + handleSize, 
              width: handleSize/2, height: this.height - handleSize*2 },
            { type: 's', x: this.x + handleSize, y: this.y + this.height - handleSize/2, 
              width: this.width - handleSize*2, height: handleSize/2 }
        ];
    }
    
    handleMouseDown(x, y, button = 0) {
        if (!this.visible) return false;
        
        // Check children first (top to bottom)
        for (let i = this.children.length - 1; i >= 0; i--) {
            if (this.children[i].handleMouseDown(x, y, button)) {
                return true;
            }
        }
        
        if (!this.contains(x, y)) return false;
        
        // Check resize handles
        if (this.resizable) {
            const handles = this.getResizeHandles();
            for (const handle of handles) {
                if (x >= handle.x && x <= handle.x + handle.width &&
                    y >= handle.y && y <= handle.y + handle.height) {
                    this.isResizing = true;
                    this.resizeHandle = handle.type;
                    this.dragStartX = x;
                    this.dragStartY = y;
                    this.focus();
                    return true;
                }
            }
        }
        
        // Check title bar for dragging
        const titleBar = this.getTitleBarBounds();
        if (titleBar && this.movable && 
            x >= titleBar.x && x <= titleBar.x + titleBar.width &&
            y >= titleBar.y && y <= titleBar.y + titleBar.height) {
            this.isDragging = true;
            this.dragStartX = x - this.x;
            this.dragStartY = y - this.y;
            this.focus();
            return true;
        }
        
        // Check region components
        for (const [region, component] of this.regions) {
            const bounds = this.regionBounds.get(region);
            if (bounds && x >= bounds.x && x <= bounds.x + bounds.width &&
                y >= bounds.y && y <= bounds.y + bounds.height) {
                if (component.handleMouseDown) {
                    const handled = component.handleMouseDown(x - bounds.x, y - bounds.y, button);
                    if (handled) {
                        this.focus();
                        return true;
                    }
                }
            }
        }
        
        // Focus this window if clicked
        this.focus();
        return true;
    }
    
    handleMouseMove(x, y) {
        if (!this.visible) return false;
        
        let handled = false;
        
        // Update children
        this.children.forEach(child => {
            if (child.handleMouseMove(x, y)) {
                handled = true;
            }
        });
        
        if (this.isDragging && this.movable) {
            this.setPosition(x - this.dragStartX, y - this.dragStartY);
            return true;
        }
        
        if (this.isResizing && this.resizable) {
            const dx = x - this.dragStartX;
            const dy = y - this.dragStartY;
            
            switch (this.resizeHandle) {
                case 'se':
                    this.setSize(
                        Math.max(200, this.width + dx),
                        Math.max(150, this.height + dy)
                    );
                    break;
                case 'e':
                    this.setSize(Math.max(200, this.width + dx), this.height);
                    break;
                case 's':
                    this.setSize(this.width, Math.max(150, this.height + dy));
                    break;
            }
            
            this.dragStartX = x;
            this.dragStartY = y;
            return true;
        }
        
        return handled;
    }
    
    handleMouseUp(x, y, button = 0) {
        if (!this.visible) return false;
        
        let handled = false;
        
        // Update children
        this.children.forEach(child => {
            if (child.handleMouseUp(x, y, button)) {
                handled = true;
            }
        });
        
        if (this.isDragging || this.isResizing) {
            this.isDragging = false;
            this.isResizing = false;
            this.resizeHandle = null;
            return true;
        }
        
        return handled;
    }
    
    focus() {
        this.focused = true;
        if (this.parent) {
            this.parent.bringChildToFront(this);
        }
    }
    
    bringChildToFront(child) {
        const index = this.children.indexOf(child);
        if (index > -1) {
            this.children.splice(index, 1);
            this.children.push(child);
        }
    }
    
    draw(ctx) {
        if (!this.visible) return;
        
        ctx.save();
        
        // Draw main container background
        ctx.fillStyle = this.backgroundColor;
        ctx.fillRect(this.x, this.y, this.width, this.height);
        
        // Draw border
        ctx.strokeStyle = this.focused ? '#0080ff' : this.borderColor;
        ctx.lineWidth = this.borderThickness;
        ctx.strokeRect(this.x, this.y, this.width, this.height);
        
        // Draw title bar
        if (this.title) {
            const titleBar = this.getTitleBarBounds();
            ctx.fillStyle = this.focused ? 'rgba(0, 128, 255, 0.3)' : 'rgba(100, 100, 100, 0.3)';
            ctx.fillRect(titleBar.x, titleBar.y, titleBar.width, titleBar.height);
            
            ctx.fillStyle = '#ffffff';
            ctx.font = '12px Arial';
            ctx.textAlign = 'left';
            ctx.textBaseline = 'middle';
            ctx.fillText(this.title, titleBar.x + 8, titleBar.y + titleBar.height / 2);
        }
        
        // Draw region separators
        ctx.strokeStyle = '#444444';
        ctx.lineWidth = 1;
        
        this.regionBounds.forEach((bounds, region) => {
            if (region !== BorderRegion.CENTER) {
                ctx.strokeRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        });
        
        // Draw region components
        this.regions.forEach((component, region) => {
            const bounds = this.regionBounds.get(region);
            if (bounds && component.draw) {
                ctx.save();
                ctx.translate(bounds.x, bounds.y);
                // Set clipping to region bounds
                ctx.beginPath();
                ctx.rect(0, 0, bounds.width, bounds.height);
                ctx.clip();
                component.draw(ctx);
                ctx.restore();
            }
        });
        
        // Draw resize handles
        if (this.resizable && this.focused) {
            ctx.fillStyle = '#0080ff';
            const handles = this.getResizeHandles();
            handles.forEach(handle => {
                ctx.fillRect(handle.x, handle.y, handle.width, handle.height);
            });
        }
        
        // Draw children (nested containers)
        this.children.forEach(child => {
            child.draw(ctx);
        });
        
        ctx.restore();
    }
    
    minimize() {
        this.minimized = !this.minimized;
        // Could implement minimize behavior here
    }
    
    close() {
        if (this.closable && this.parent) {
            this.parent.removeChild(this);
        }
    }
    
    removeChild(child) {
        this.children = this.children.filter(c => c !== child);
        // Remove from regions if present
        for (const [region, component] of this.regions) {
            if (component === child) {
                this.regions.delete(region);
                break;
            }
        }
        this.updateLayout();
    }
}

// Simple text component for border layout regions
export class TextComponent {
    constructor(text, options = {}) {
        this.text = text;
        this.fontSize = options.fontSize || 12;
        this.color = options.color || '#ffffff';
        this.backgroundColor = options.backgroundColor || 'transparent';
        this.padding = options.padding || 5;
        this.alignment = options.alignment || 'left'; // left, center, right
        this.x = 0;
        this.y = 0;
        this.width = 100;
        this.height = 30;
    }
    
    setPosition(x, y) {
        this.x = x;
        this.y = y;
    }
    
    setSize(width, height) {
        this.width = width;
        this.height = height;
    }
    
    draw(ctx) {
        // Draw background
        if (this.backgroundColor !== 'transparent') {
            ctx.fillStyle = this.backgroundColor;
            ctx.fillRect(0, 0, this.width, this.height);
        }
        
        // Draw text
        ctx.fillStyle = this.color;
        ctx.font = `${this.fontSize}px Arial`;
        ctx.textBaseline = 'top';
        
        const lines = this.text.split('\n');
        const lineHeight = this.fontSize + 2;
        
        lines.forEach((line, index) => {
            let x = this.padding;
            if (this.alignment === 'center') {
                ctx.textAlign = 'center';
                x = this.width / 2;
            } else if (this.alignment === 'right') {
                ctx.textAlign = 'right';
                x = this.width - this.padding;
            } else {
                ctx.textAlign = 'left';
            }
            
            const y = this.padding + index * lineHeight;
            ctx.fillText(line, x, y);
        });
    }
}

// Window manager to handle multiple top-level windows
export class WindowManager {
    constructor() {
        this.windows = [];
        this.activeWindow = null;
    }
    
    addWindow(window) {
        this.windows.push(window);
        this.activeWindow = window;
        window.focus();
        return window;
    }
    
    removeWindow(window) {
        this.windows = this.windows.filter(w => w !== window);
        if (this.activeWindow === window) {
            this.activeWindow = this.windows.length > 0 ? this.windows[this.windows.length - 1] : null;
        }
    }
    
    handleMouseDown(x, y, button = 0) {
        // Check windows from top to bottom
        for (let i = this.windows.length - 1; i >= 0; i--) {
            if (this.windows[i].handleMouseDown(x, y, button)) {
                this.bringToFront(this.windows[i]);
                this.activeWindow = this.windows[i];
                return true;
            }
        }
        return false;
    }
    
    handleMouseMove(x, y) {
        this.windows.forEach(window => {
            window.handleMouseMove(x, y);
        });
    }
    
    handleMouseUp(x, y, button = 0) {
        this.windows.forEach(window => {
            window.handleMouseUp(x, y, button);
        });
    }
    
    bringToFront(window) {
        const index = this.windows.indexOf(window);
        if (index > -1) {
            this.windows.splice(index, 1);
            this.windows.push(window);
            
            // Update focus states
            this.windows.forEach(w => w.focused = false);
            window.focused = true;
        }
    }
    
    draw(ctx) {
        // Draw windows in order (bottom to top)
        this.windows.forEach(window => {
            window.draw(ctx);
        });
    }
}