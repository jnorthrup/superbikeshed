// Caption class definition will be pasted here.
class Caption {
            constructor(x, y, text, color = '#fff', size = 12) {
                this.x = x;
                this.y = y;
                this.text = text;
                this.color = color;
                this.size = size;
                this.life = 60; // 1 second
                this.vy = -0.5; // Float upward
                this.alpha = 1;
            }

            update() {
                this.y += this.vy;
                this.life--;
                this.alpha = Math.min(1, this.life / 20);
            }

            draw(ctx, camera) {
                const screenX = (this.x - camera.x) * camera.zoom + camera.canvasWidth / 2;
                const screenY = (this.y - camera.y) * camera.zoom + camera.canvasHeight / 2;

                ctx.save();
                ctx.globalAlpha = this.alpha;
                ctx.fillStyle = this.color;
                ctx.font = `${this.size * camera.zoom}px Arial`;
                ctx.textAlign = 'center';
                ctx.strokeStyle = '#000';
                ctx.lineWidth = 3;
                ctx.strokeText(this.text, screenX, screenY);
                ctx.fillText(this.text, screenX, screenY);
                ctx.restore();
            }
        }

export { Caption };
