package com.phantom_strike;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;

public class StickmanGame extends ApplicationAdapter {    // Constants
    private static final int WORLD_WIDTH = 4000;  // Much wider world
    private static final int WORLD_HEIGHT = 1200;  // Taller world
    private static final float STICKMAN_WIDTH = 20;
    private static final float STICKMAN_HEIGHT = 50;
    private static final float MOVEMENT_SPEED = 250;  // Slightly faster movement
    private static final float BULLET_SPEED = 400;  // Faster bullets
    private static final float BULLET_RADIUS = 5;
    private static final float RESPAWN_TIME = 2.0f;
    private static final float BULLET_DAMAGE = 25;  // Made explicit
    private static final float MAX_AI_SIGHT_RANGE = 800;  // How far AI can see enemies
    private static final float HEALTH_PACK_RESTORE = 50;  // Amount of health restored by health pack
    
    // Renderers
    private ShapeRenderer shapeRenderer;
    private SpriteBatch batch;
    private BitmapFont font;
    
    // Camera and viewport
    private OrthographicCamera camera;
    private Viewport viewport;
    
    // Game objects
    private Array<Player> players;
    private Array<Bullet> bullets;
    private Array<GameObject> gameObjects;
    private Player localPlayer;
    
    // Game state
    private int redTeamScore = 0;
    private int blueTeamScore = 0;
    private float gameTime = 0;
      // Add a cooldown timer for shooting
    private float shootCooldownTimer = 0;
    private static final float SHOOT_COOLDOWN = 1.5f; // Increased from the previous value
    
    // Add a global cooldown timer for shooting
    private float globalShootCooldownTimer = 0;
    private static final float GLOBAL_SHOOT_COOLDOWN = 0.5f; // 0.5 seconds cooldown for all players and AI
    
    @Override
    public void create() {
        try {            // Set debug level
            Gdx.app.setLogLevel(com.badlogic.gdx.Application.LOG_DEBUG);
            
            // Initialize rendering tools
            shapeRenderer = new ShapeRenderer();
            batch = new SpriteBatch();
            
            // Use a default font instead of loading from assets to avoid errors
            font = new BitmapFont();
              // Set up camera with a more reasonable view size (not the entire massive world)
            camera = new OrthographicCamera();
            // Use a smaller viewport size that's more appropriate for viewing
            viewport = new FitViewport(1280, 720, camera);  
            camera.position.set(viewport.getWorldWidth() / 2, viewport.getWorldHeight() / 2, 0);
            
            // Initialize collections
            players = new Array<>();
            bullets = new Array<>();
            gameObjects = new Array<>();
            
            // Create game objects
            createGameObjects();
            
            // Create players (4 per team for this example)
            createTestPlayers();
            
            // Set local player (for testing)
            localPlayer = players.get(0);
            
            Gdx.app.log("StickmanGame", "Game initialized successfully");
        } catch (Exception e) {
            Gdx.app.error("StickmanGame", "Error during initialization", e);
        }
    }
      private void createTestPlayers() {
        // Create Red Team (8 players for larger world)
        for (int i = 0; i < 8; i++) {
            // Place players at different heights and distances on left side of map
            float xPos = MathUtils.random(100, WORLD_WIDTH/2 - 400);
            float yPos = MathUtils.random(100, WORLD_HEIGHT - 100);
            
            Player redPlayer = new Player(xPos, yPos, true);
            
            // First player is human-controlled
            if (i > 0) redPlayer.isAI = true;
            
            players.add(redPlayer);
        }
        
        // Create Blue Team (8 players for larger world)
        for (int i = 0; i < 8; i++) {
            // Place players at different heights and distances on right side of map
            float xPos = MathUtils.random(WORLD_WIDTH/2 + 400, WORLD_WIDTH - 100);
            float yPos = MathUtils.random(100, WORLD_HEIGHT - 100);
            
            Player bluePlayer = new Player(xPos, yPos, false);
            bluePlayer.isAI = true;
            
            players.add(bluePlayer);
        }
    }    @Override
    public void render() {
        try {
            float deltaTime = Gdx.graphics.getDeltaTime();
            gameTime += deltaTime;
            
            // Update the cooldown timer
            if (shootCooldownTimer > 0) {
                shootCooldownTimer -= deltaTime;
                if (shootCooldownTimer <= 0) {
                    Gdx.app.debug("Cooldown", "Player shoot cooldown reset");
                }
            }
            
            // Update the global cooldown timer
            if (globalShootCooldownTimer > 0) {
                globalShootCooldownTimer -= deltaTime;
                if (globalShootCooldownTimer <= 0) {
                    Gdx.app.debug("Cooldown", "Global shoot cooldown reset");
                }
            }            // Update game state
            if (gameOver) {
                gameOverMessageTime += deltaTime;
                
                // Allow restarting the game with ENTER key
                if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                    resetGame();
                }
            } else {
                // Check for victory condition
                checkVictoryConditions();
                
                // Assign AI roles every 5 seconds to adapt to changing situations
                if (gameTime % 5 < deltaTime) {
                    assignTeamRoles();
                }
                
                // Only update game logic if game is still in progress
                updatePlayers(deltaTime);
                updateBullets(deltaTime);
                updateAI(deltaTime);
                checkCollisions();
                checkBulletObjectCollisions();
            }
              // Always update camera
            updateCamera();
            
            // Clear screen
            Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            
            // Set projection matrix for all rendering
            camera.update();
            shapeRenderer.setProjectionMatrix(camera.combined);
            batch.setProjectionMatrix(camera.combined);
            
            // Log the viewport and camera info occasionally to debug
            if (gameTime % 5 < 0.1f) {
                Gdx.app.debug("Render", "Camera at " + camera.position.x + "," + camera.position.y + 
                           " Viewport: " + viewport.getWorldWidth() + "x" + viewport.getWorldHeight());
            }
            
            // Draw background
            drawBackground();
            
            // Draw game objects
            drawGameObjects();
            
            // Draw players
            shapeRenderer.begin(ShapeType.Filled);
            for (Player player : players) {
                if (!player.isRespawning) {
                    drawStickman(player);
                }
            }
            shapeRenderer.end();              // Draw bullets - with extra debug info
            shapeRenderer.begin(ShapeType.Filled);
            Gdx.app.debug("Drawing Bullets", "Current bullet count: " + bullets.size);
            for (Bullet bullet : bullets) {
                drawBullet(bullet);
            }
            shapeRenderer.end();
              // Draw minimap to help navigation in the larger world
            drawMinimap();
            
            // Draw UI elements (score, etc)
            drawUI();
            
            // Draw debug info
            drawDebugInfo();
        } catch (Exception e) {
            Gdx.app.error("StickmanGame", "Error during render", e);
        }
    }
    
    // Check if either team has achieved victory
    private void checkVictoryConditions() {
        // Victory by score
        if (redTeamScore >= SCORE_TO_WIN) {
            gameOver = true;
            winningTeam = "RED";
        } else if (blueTeamScore >= SCORE_TO_WIN) {
            gameOver = true;
            winningTeam = "BLUE";
        }
        
        // Victory by elimination (all players on one team dead)
        boolean anyRedAlive = false;
        boolean anyBlueAlive = false;
        
        for (Player player : players) {
            if (!player.isRespawning) {
                if (player.isRedTeam) {
                    anyRedAlive = true;
                } else {
                    anyBlueAlive = true;
                }
            }
        }
        
        if (!anyRedAlive && redTeamScore < SCORE_TO_WIN && blueTeamScore < SCORE_TO_WIN) {
            gameOver = true;
            winningTeam = "BLUE";
            blueTeamScore = SCORE_TO_WIN; // Set score to victory threshold
        } else if (!anyBlueAlive && redTeamScore < SCORE_TO_WIN && blueTeamScore < SCORE_TO_WIN) {
            gameOver = true;
            winningTeam = "RED";
            redTeamScore = SCORE_TO_WIN; // Set score to victory threshold
        }
    }
    
    // Reset the game state
    private void resetGame() {
        // Reset scores
        redTeamScore = 0;
        blueTeamScore = 0;
        
        // Reset game state
        gameOver = false;
        winningTeam = "";
        gameOverMessageTime = 0;
        
        // Clear existing bullets
        bullets.clear();
        
        // Reset players
        for (Player player : players) {
            player.health = 100;
            player.isRespawning = false;
            
            // Reset positions to team sides
            if (player.isRedTeam) {
                player.position.set(MathUtils.random(50, WORLD_WIDTH/2 - 400), 
                                   MathUtils.random(100, WORLD_HEIGHT - 100));
            } else {
                player.position.set(MathUtils.random(WORLD_WIDTH/2 + 400, WORLD_WIDTH - 100), 
                                   MathUtils.random(100, WORLD_HEIGHT - 100));
            }
        }
    }    private void drawBackground() {
        shapeRenderer.begin(ShapeType.Filled);
        
        // Draw ground
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1);
        shapeRenderer.rect(0, 0, WORLD_WIDTH, 60);
        
        // Draw team territories with subtle coloring
        // Red team territory (left side)
        shapeRenderer.setColor(0.15f, 0.1f, 0.1f, 1);
        shapeRenderer.rect(0, 60, WORLD_WIDTH/2, WORLD_HEIGHT - 60);
        
        // Blue team territory (right side)
        shapeRenderer.setColor(0.1f, 0.1f, 0.15f, 1);
        shapeRenderer.rect(WORLD_WIDTH/2, 60, WORLD_WIDTH/2, WORLD_HEIGHT - 60);
        
        // Draw dividing line between territories
        shapeRenderer.setColor(0.9f, 0.9f, 0.9f, 1);
        shapeRenderer.rectLine(WORLD_WIDTH/2, 0, WORLD_WIDTH/2, WORLD_HEIGHT, 3);
        
        // Only draw grid within camera's view to improve performance
        // Calculate visible area
        float camLeft = camera.position.x - viewport.getWorldWidth()/2;
        float camRight = camera.position.x + viewport.getWorldWidth()/2;
        float camBottom = camera.position.y - viewport.getWorldHeight()/2;
        float camTop = camera.position.y + viewport.getWorldHeight()/2;
        
        // Extend slightly to avoid pop-in
        camLeft -= 200;
        camRight += 200;
        camBottom -= 200;
        camTop += 200;
        
        // Draw grid for reference/scale (more subtle in larger world)
        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 0.2f);
        
        // Vertical grid lines - only draw lines in visible area
        int gridSpacing = 200;
        int startX = Math.max(0, ((int)camLeft / gridSpacing) * gridSpacing);
        for (int x = startX; x <= Math.min(WORLD_WIDTH, camRight); x += gridSpacing) {
            shapeRenderer.rectLine(x, Math.max(0, camBottom), x, Math.min(WORLD_HEIGHT, camTop), 1);
        }
        
        // Horizontal grid lines - only draw lines in visible area
        int startY = Math.max(0, ((int)camBottom / gridSpacing) * gridSpacing);
        for (int y = startY; y <= Math.min(WORLD_HEIGHT, camTop); y += gridSpacing) {
            shapeRenderer.rectLine(Math.max(0, camLeft), y, Math.min(WORLD_WIDTH, camRight), y, 1);
        }
        
        shapeRenderer.end();
    }
    
    private void drawStickman(Player player) {
        float x = player.position.x;
        float y = player.position.y;
        
        // Set team color with a slight glow effect
        if (player.isRedTeam) {
            shapeRenderer.setColor(1.0f, 0.2f, 0.2f, 1.0f); // Red team
        } else {
            shapeRenderer.setColor(0.2f, 0.2f, 1.0f, 1.0f); // Blue team
        }
        
        // Draw a small glow effect if this is the local player
        if (player == localPlayer) {
            float pulseIntensity = 0.5f + 0.5f * MathUtils.sin(gameTime * 5);
            shapeRenderer.setColor(player.isRedTeam ? 
                new Color(1f, 0.5f * pulseIntensity, 0.5f * pulseIntensity, 1f) :
                new Color(0.5f * pulseIntensity, 0.5f * pulseIntensity, 1f, 1f));
        }
        
        // Calculate animation offset for arms and legs based on velocity
        float animSpeed = 5f;
        float limbSwing = MathUtils.sin(gameTime * animSpeed) * 
                         Math.min(Math.abs(player.velocity.x) / 100f, 1f) * 10f;
                         
        // Head (with a slight bobbing effect based on movement)
        float headBob = Math.abs(limbSwing) * 0.1f;
        shapeRenderer.circle(x + STICKMAN_WIDTH/2, y + STICKMAN_HEIGHT - 8 + headBob, 8);
        
        // Body (smooth line)
        shapeRenderer.rectLine(
            x + STICKMAN_WIDTH/2, y + STICKMAN_HEIGHT - 16, 
            x + STICKMAN_WIDTH/2, y + 12, 
            3);
            
        // Arms (with swing animation)
        if (player.isShooting) {
            // Shooting pose - arm extended forward
            shapeRenderer.rectLine(
                x + STICKMAN_WIDTH/2, y + STICKMAN_HEIGHT - 20,
                x + STICKMAN_WIDTH/2 + (player.facingRight ? 20 : -20), y + STICKMAN_HEIGHT - 20,
                2.5f);
        } else {
            // Walking animation for arms
            shapeRenderer.rectLine(
                x + STICKMAN_WIDTH/2, y + STICKMAN_HEIGHT - 20,
                x + STICKMAN_WIDTH/2 - limbSwing, y + STICKMAN_HEIGHT - 30,
                2.5f);
            
            shapeRenderer.rectLine(
                x + STICKMAN_WIDTH/2, y + STICKMAN_HEIGHT - 20,
                x + STICKMAN_WIDTH/2 + limbSwing, y + STICKMAN_HEIGHT - 30,
                2.5f);
        }
        
        // Legs (with walking animation)
        shapeRenderer.rectLine(
            x + STICKMAN_WIDTH/2, y + 12,
            x + STICKMAN_WIDTH/2 - limbSwing, y,
            2.5f);
            
        shapeRenderer.rectLine(
            x + STICKMAN_WIDTH/2, y + 12,
            x + STICKMAN_WIDTH/2 + limbSwing, y,
            2.5f);
    }
      private void drawBullet(Bullet bullet) {
        // Bullet with team color
        if (bullet.isRedTeam) {
            shapeRenderer.setColor(1.0f, 0.4f, 0.4f, 1.0f);
        } else {
            shapeRenderer.setColor(0.4f, 0.4f, 1.0f, 1.0f);
        }
        
        // Draw bullet with a slight trail effect
        shapeRenderer.circle(bullet.position.x, bullet.position.y, BULLET_RADIUS);
        
        // Bullet trail
        for (int i = 1; i <= 3; i++) {
            float trailX = bullet.position.x - bullet.direction.x * i * 2f;
            float trailY = bullet.position.y - bullet.direction.y * i * 2f;
            float alpha = 1f - (i / 4f);
            float size = BULLET_RADIUS * (1f - i * 0.25f);
            shapeRenderer.setColor(bullet.isRedTeam ? 
                new Color(1f, 0.4f, 0.4f, alpha) : 
                new Color(0.4f, 0.4f, 1f, alpha));
            shapeRenderer.circle(trailX, trailY, size);
        }
    }    private void drawUI() {
        batch.begin();
        
        // Get camera position for UI alignment
        float camX = camera.position.x;
        float camY = camera.position.y;
        float viewWidth = viewport.getWorldWidth();
        float viewHeight = viewport.getWorldHeight();
        
        // Draw scores with more prominence
        font.getData().setScale(1.5f);
        String scoreText = "RED " + redTeamScore + " - " + blueTeamScore + " BLUE";
        font.draw(batch, scoreText, camX - viewWidth/2 + 20, camY + viewHeight/2 - 20);
        
        // Display victory message when a team wins
        if (gameOver) {
            font.getData().setScale(2.0f);
            
            // Make text pulse for attention
            float pulseAmount = 1.0f + 0.2f * MathUtils.sin(gameOverMessageTime * 5f);
            font.getData().setScale(2.0f * pulseAmount);
            
            String victoryText = winningTeam + " TEAM WINS!";
            
            // Center the text on the screen
            font.setColor(winningTeam.equals("RED") ? Color.RED : Color.BLUE);
            font.draw(batch, victoryText, camX, camY + 50, 0, Align.center, false);
            font.setColor(Color.WHITE);
            
            // Additional info
            font.getData().setScale(1.0f);
            font.draw(batch, "Press ENTER to restart", camX, camY - 50, 0, Align.center, false);
        }
        
        // Reset font scale for other UI elements
        font.getData().setScale(1.0f);
        batch.end();
    }    private void drawDebugInfo() {
        batch.begin();
        // Anchor debug info to camera view
        float startX = camera.position.x - viewport.getWorldWidth()/2 + 20;
        float startY = camera.position.y + viewport.getWorldHeight()/2 - 60; // Lower position to avoid overlap with score
        
        font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), startX, startY);
        font.draw(batch, "Players: " + players.size, startX, startY - 20);
        
        // Count active players per team
        int redActive = 0;
        int blueActive = 0;
        for (Player p : players) {
            if (!p.isRespawning) {
                if (p.isRedTeam) redActive++;
                else blueActive++;
            }
        }
        
        font.draw(batch, "Red Team: " + redActive + " active", startX, startY - 40);
        font.draw(batch, "Blue Team: " + blueActive + " active", startX, startY - 60);
        font.draw(batch, "Bullets: " + bullets.size, startX, startY - 80);
        
        if (localPlayer != null) {
            font.draw(batch, "Health: " + localPlayer.health, startX, startY - 100);
            
            // Show local player position and camera position
            font.draw(batch, "Player: " + (int)localPlayer.position.x + "," + (int)localPlayer.position.y, 
                     startX, startY - 120);
            font.draw(batch, "Camera: " + (int)camera.position.x + "," + (int)camera.position.y, 
                     startX, startY - 140);
            
            // Display victory condition
            font.draw(batch, "Victory at: " + SCORE_TO_WIN + " kills", startX, startY - 160);
            
            // Show world dimensions
            font.draw(batch, "World: " + WORLD_WIDTH + "x" + WORLD_HEIGHT, startX, startY - 180);
        }
        
        batch.end();
    }
      private void createGameObjects() {
        // Create multiple platforms throughout the larger world
        // Central area platforms
        gameObjects.add(new GameObject(WORLD_WIDTH/2 - 200, 200, 400, 20, new Color(0.6f, 0.6f, 0.6f, 1f), GameObjectType.PLATFORM));
        
        // Team zone platforms - for red team (left side)
        for (int i = 0; i < 5; i++) {
            float x = MathUtils.random(100, WORLD_WIDTH/2 - 300);
            float y = MathUtils.random(150, WORLD_HEIGHT - 200);
            float width = MathUtils.random(80, 180);
            gameObjects.add(new GameObject(x, y, width, 20, new Color(0.8f, 0.3f, 0.3f, 0.8f), GameObjectType.PLATFORM));
        }
        
        // Team zone platforms - for blue team (right side)
        for (int i = 0; i < 5; i++) {
            float x = MathUtils.random(WORLD_WIDTH/2 + 300, WORLD_WIDTH - 200);
            float y = MathUtils.random(150, WORLD_HEIGHT - 200);
            float width = MathUtils.random(80, 180);
            gameObjects.add(new GameObject(x, y, width, 20, new Color(0.3f, 0.3f, 0.8f, 0.8f), GameObjectType.PLATFORM));
        }
        
        // Add cover objects - scattered across the world
        for (int i = 0; i < 20; i++) {
            boolean onLeftSide = MathUtils.randomBoolean();
            float x;
            if (onLeftSide) {
                x = MathUtils.random(100, WORLD_WIDTH/2 - 100);
            } else {
                x = MathUtils.random(WORLD_WIDTH/2 + 100, WORLD_WIDTH - 100);
            }
            float y = MathUtils.random(100, WORLD_HEIGHT - 200);
            float width = MathUtils.random(30, 60);
            float height = MathUtils.random(50, 90);
            gameObjects.add(new GameObject(x, y, width, height, new Color(0.5f, 0.5f, 0.5f, 1f), GameObjectType.COVER));
        }
        
        // Add barriers in the central area (no-man's land)
        for (int i = 0; i < 10; i++) {
            float x = MathUtils.random(WORLD_WIDTH/2 - 300, WORLD_WIDTH/2 + 300);
            float y = MathUtils.random(100, WORLD_HEIGHT - 200);
            float width = MathUtils.random(40, 80);
            float height = MathUtils.random(60, 120);
            gameObjects.add(new GameObject(x, y, width, height, new Color(0.4f, 0.4f, 0.4f, 1f), GameObjectType.BARRIER));
        }
        
        // Add circular obstacles throughout the map
        for (int i = 0; i < 30; i++) {
            float x = MathUtils.random(100, WORLD_WIDTH - 100);
            float y = MathUtils.random(100, WORLD_HEIGHT - 100);
            float size = MathUtils.random(20, 50);
            gameObjects.add(new GameObject(x, y, size, size, new Color(0.3f, 0.3f, 0.3f, 1f), GameObjectType.OBSTACLE));
        }
        
        // Add health packs - fewer of these, they're power-ups
        for (int i = 0; i < 8; i++) {
            float x = MathUtils.random(100, WORLD_WIDTH - 100);
            float y = MathUtils.random(100, WORLD_HEIGHT - 100);
            gameObjects.add(new GameObject(x, y, 30, 30, new Color(0.2f, 0.9f, 0.2f, 1f), GameObjectType.HEALTH_PACK));
        }
        
        // Add teleporters (linked pairs)
        for (int i = 0; i < 3; i++) {
            // First teleporter
            float x1 = MathUtils.random(100, WORLD_WIDTH/2 - 200);
            float y1 = MathUtils.random(100, WORLD_HEIGHT - 100);
            
            // Second teleporter (linked)
            float x2 = MathUtils.random(WORLD_WIDTH/2 + 200, WORLD_WIDTH - 100);
            float y2 = MathUtils.random(100, WORLD_HEIGHT - 100);
            
            GameObject teleporter1 = new GameObject(x1, y1, 40, 40, new Color(0.8f, 0.2f, 0.8f, 1f), GameObjectType.TELEPORTER);
            GameObject teleporter2 = new GameObject(x2, y2, 40, 40, new Color(0.8f, 0.2f, 0.8f, 1f), GameObjectType.TELEPORTER);
            
            // Link teleporters (store reference to destination in the userData field)
            teleporter1.userData = teleporter2;
            teleporter2.userData = teleporter1;
            
            gameObjects.add(teleporter1);
            gameObjects.add(teleporter2);
        }
    }
      private void drawGameObjects() {
        shapeRenderer.begin(ShapeType.Filled);
        for (GameObject obj : gameObjects) {
            // Update object time-based effects
            obj.update(Gdx.graphics.getDeltaTime());
            
            // Base color (may be modified based on effects)
            Color renderColor = obj.color.cpy();
            
            // Draw different shapes based on type
            switch (obj.type) {
                case PLATFORM:
                    shapeRenderer.setColor(renderColor);
                    shapeRenderer.rect(obj.position.x, obj.position.y, obj.width, obj.height);
                    break;
                    
                case OBSTACLE:
                    shapeRenderer.setColor(renderColor);
                    shapeRenderer.circle(obj.position.x + obj.width/2, obj.position.y + obj.height/2, obj.width/2);
                    break;
                    
                case COVER:
                    shapeRenderer.setColor(renderColor);
                    shapeRenderer.rect(obj.position.x, obj.position.y, obj.width, obj.height);
                    // Draw a crenellation on top for cover
                    shapeRenderer.setColor(renderColor.r * 1.2f, renderColor.g * 1.2f, renderColor.b * 1.2f, 1f);
                    for (int i = 0; i < 4; i++) {
                        float segWidth = obj.width / 4;
                        if (i % 2 == 0) {
                            shapeRenderer.rect(obj.position.x + i * segWidth, obj.position.y + obj.height, 
                                             segWidth, 10);
                        }
                    }
                    break;
                    
                case BARRIER:
                    // Barriers are solid blocks
                    shapeRenderer.setColor(renderColor);
                    shapeRenderer.rect(obj.position.x, obj.position.y, obj.width, obj.height);
                    
                    // Draw cross-hatching effect
                    shapeRenderer.setColor(renderColor.r * 0.8f, renderColor.g * 0.8f, renderColor.b * 0.8f, 1f);
                    for (int i = 0; i < obj.width; i += 10) {
                        shapeRenderer.line(obj.position.x + i, obj.position.y, 
                                           obj.position.x, obj.position.y + i);
                        shapeRenderer.line(obj.position.x + i, obj.position.y + obj.height, 
                                           obj.position.x + obj.width, obj.position.y + i);
                    }
                    break;
                    
                case HEALTH_PACK:
                    // Health packs pulse with a green glow
                    float pulse = 0.7f + 0.3f * MathUtils.sin(obj.effectTimer * 5);
                    shapeRenderer.setColor(renderColor.r * pulse, renderColor.g * pulse, renderColor.b * pulse, 1f);
                    
                    // Draw as a cross shape (like a medical symbol)
                    float centerX = obj.position.x + obj.width/2;
                    float centerY = obj.position.y + obj.height/2;
                    float crossWidth = obj.width * 0.3f;
                    
                    // Horizontal part of cross
                    shapeRenderer.rect(centerX - obj.width/2, centerY - crossWidth/2, obj.width, crossWidth);
                    // Vertical part of cross
                    shapeRenderer.rect(centerX - crossWidth/2, centerY - obj.height/2, crossWidth, obj.height);
                    // Circle around cross
                    shapeRenderer.setColor(renderColor.r * 0.8f * pulse, renderColor.g * 0.8f * pulse, renderColor.b * 0.8f * pulse, 0.5f);
                    shapeRenderer.circle(centerX, centerY, obj.width/2);
                    break;
                    
                case TELEPORTER:
                    // Teleporters have a pulsing effect
                    float tPulse = 0.6f + 0.4f * MathUtils.sin(obj.effectTimer * 3);
                    shapeRenderer.setColor(renderColor.r * tPulse, renderColor.g * tPulse, renderColor.b * tPulse, 1f);
                    
                    // Draw teleporter as a circle with inner swirl
                    float tcenterX = obj.position.x + obj.width/2;
                    float tcenterY = obj.position.y + obj.height/2;
                    shapeRenderer.circle(tcenterX, tcenterY, obj.width/2);
                    
                    // Draw swirl effect
                    shapeRenderer.setColor(0.9f, 0.9f, 0.9f, 0.8f);
                    float angle = obj.effectTimer * 120; // rotation speed
                    float spiralRadius = obj.width * 0.35f;
                    
                    for (float t = 0; t < 360; t += 30) {
                        float rad = (float)Math.toRadians(t + angle);
                        float x1 = tcenterX + spiralRadius * MathUtils.cos(rad);
                        float y1 = tcenterY + spiralRadius * MathUtils.sin(rad);
                        float rad2 = (float)Math.toRadians(t + angle + 15);
                        float x2 = tcenterX + (spiralRadius * 0.5f) * MathUtils.cos(rad2);
                        float y2 = tcenterY + (spiralRadius * 0.5f) * MathUtils.sin(rad2);
                        
                        shapeRenderer.rectLine(x1, y1, x2, y2, 3f);
                    }
                    break;
            }
        }
        shapeRenderer.end();
    }
    
    // Additional collision detection for game objects
    private void handleObjectCollisions(Player player, float deltaTime) {
        // Simple collision resolution
        for (GameObject obj : gameObjects) {
            Rectangle playerRect = new Rectangle(
                player.position.x, player.position.y, 
                STICKMAN_WIDTH, STICKMAN_HEIGHT
            );
            
            if (Intersector.overlaps(playerRect, obj.getBounds())) {
                // Handle collision based on object type
                switch (obj.type) {
                    case OBSTACLE:
                        // Push player away from obstacle
                        Vector2 pushDirection = new Vector2(
                            player.position.x + STICKMAN_WIDTH/2 - (obj.position.x + obj.width/2),
                            player.position.y + STICKMAN_HEIGHT/2 - (obj.position.y + obj.height/2)
                        ).nor();
                        player.position.add(pushDirection.x * MOVEMENT_SPEED * deltaTime, 
                                           pushDirection.y * MOVEMENT_SPEED * deltaTime);
                        player.velocity.scl(0.5f); // Slow down player
                        break;
                        
                    case BARRIER:
                        // Barriers completely block movement
                        Vector2 barrierPush = new Vector2(
                            player.position.x + STICKMAN_WIDTH/2 - (obj.position.x + obj.width/2),
                            player.position.y + STICKMAN_HEIGHT/2 - (obj.position.y + obj.height/2)
                        ).nor();
                        // Stronger push than regular obstacles
                        player.position.add(barrierPush.x * MOVEMENT_SPEED * 2 * deltaTime, 
                                           barrierPush.y * MOVEMENT_SPEED * 2 * deltaTime);
                        player.velocity.scl(0.1f); // Almost stop the player
                        break;
                        
                    case COVER:
                        // Stop bullets from passing through (handled in bullet update)
                        // But allow player to pass behind it
                        break;
                        
                    case PLATFORM:
                        // Handle standing on platforms
                        float playerBottom = player.position.y;
                        float platformTop = obj.position.y + obj.height;
                        
                        if (Math.abs(playerBottom - platformTop) < 5 && player.velocity.y < 0) {
                            player.position.y = platformTop;
                            player.velocity.y = 0;
                        }
                        break;
                        
                    case HEALTH_PACK:
                        // Player collects health pack
                        if (!player.isRespawning && player.health < 100) {
                            player.health = Math.min(100, player.health + (int)HEALTH_PACK_RESTORE);
                            // Remove the health pack from the game world
                            gameObjects.removeValue(obj, true);
                            // Health pack collection sound effect would go here
                            
                            // Spawn a new health pack somewhere else after a delay
                            // (This logic could be moved to a separate method)
                            float randomX = MathUtils.random(100, WORLD_WIDTH - 100);
                            float randomY = MathUtils.random(100, WORLD_HEIGHT - 100);
                            gameObjects.add(new GameObject(randomX, randomY, 30, 30, 
                                new Color(0.2f, 0.9f, 0.2f, 1f), GameObjectType.HEALTH_PACK));
                        }
                        break;
                        
                    case TELEPORTER:
                        // Teleport player to the linked teleporter
                        if (!player.isRespawning && obj.userData != null) {
                            GameObject destination = (GameObject)obj.userData;
                            // Place player slightly above destination teleporter to avoid immediate re-teleport
                            player.position.set(
                                destination.position.x + destination.width/2 - STICKMAN_WIDTH/2,
                                destination.position.y + destination.height + 10
                            );
                            // Reset player velocity to prevent carrying momentum through teleport
                            player.velocity.scl(0.1f);
                        }
                        break;
                }
            }
        }
    }
      // Check bullet collisions with objects
    private void checkBulletObjectCollisions() {
        // Use a spatial partitioning approach to reduce collision checks
        Map<GameObjectType, List<GameObject>> objectTypeMap = new HashMap<>();
        for (GameObject obj : gameObjects) {
            objectTypeMap.computeIfAbsent(obj.type, k -> new ArrayList<>()).add(obj);
        }

        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet bullet = bullets.get(i);
            Circle bulletCircle = new Circle(bullet.position, BULLET_RADIUS);

            // Check only relevant object types for collisions
            List<GameObject> relevantObjects = objectTypeMap.getOrDefault(GameObjectType.COVER, Collections.emptyList());
            relevantObjects.addAll(objectTypeMap.getOrDefault(GameObjectType.BARRIER, Collections.emptyList()));
            relevantObjects.addAll(objectTypeMap.getOrDefault(GameObjectType.OBSTACLE, Collections.emptyList()));
            relevantObjects.addAll(objectTypeMap.getOrDefault(GameObjectType.TELEPORTER, Collections.emptyList()));

            for (GameObject obj : relevantObjects) {
                if (Intersector.overlaps(bulletCircle, obj.getBounds())) {
                    switch (obj.type) {
                        case COVER:
                        case BARRIER:
                            bullets.removeIndex(i);
                            return; // Exit the loop since bullet is gone

                        case OBSTACLE:
                            Vector2 normal = new Vector2(
                                bullet.position.x - (obj.position.x + obj.width / 2),
                                bullet.position.y - (obj.position.y + obj.height / 2)
                            ).nor();
                            bullet.direction.scl(-1); // Simple bounce effect
                            bullet.position.add(normal.x * 5, normal.y * 5);
                            return; // Exit loop after handling bounce

                        case TELEPORTER:
                            if (obj.userData != null) {
                                GameObject destination = (GameObject) obj.userData;
                                bullet.position.set(
                                    destination.position.x + destination.width / 2,
                                    destination.position.y + destination.height / 2
                                );
                                return; // Exit loop after teleporting
                            }
                            break;

                        default:
                            break;
                    }
                }
            }
        }
    }
    
    private void updatePlayers(float deltaTime) {
        // Handle input for local player
        handlePlayerInput(deltaTime);
        
        // Revert to a standard for loop for compatibility with Array type
        for (Player player : players) {
            // Apply physics
            player.position.add(
                player.velocity.x * deltaTime,
                player.velocity.y * deltaTime
            );
            
            // Handle collisions with game objects
            handleObjectCollisions(player, deltaTime);
            
            // Apply friction
            player.velocity.scl(0.9f);
              // Handle respawning
            if (player.isRespawning) {
                player.respawnTimer -= deltaTime;
                if (player.respawnTimer <= 0) {
                    player.isRespawning = false;
                    player.health = 100;
                    
                    // Use the stored respawn position if available, otherwise generate a new one
                    if (player.respawnX != 0 && player.respawnY != 0) {
                        player.position.set(player.respawnX, player.respawnY);
                    } else {
                        // Fallback to old respawn logic
                        if (player.isRedTeam) {
                            player.position.set(MathUtils.random(50, WORLD_WIDTH/2 - 400), 
                                               MathUtils.random(100, WORLD_HEIGHT - 100));
                        } else {
                            player.position.set(MathUtils.random(WORLD_WIDTH/2 + 400, WORLD_WIDTH - 100), 
                                               MathUtils.random(100, WORLD_HEIGHT - 100));
                        }
                    }
                    
                    // Reset respawn position for next time
                    player.respawnX = 0;
                    player.respawnY = 0;
                }
            }
            
            // Keep players in bounds
            player.position.x = MathUtils.clamp(player.position.x, 0, WORLD_WIDTH - STICKMAN_WIDTH);
            player.position.y = MathUtils.clamp(player.position.y, 60, WORLD_HEIGHT - STICKMAN_HEIGHT);
        }
    }
      private void updateBullets(float deltaTime) {
        // Update bullet positions
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet bullet = bullets.get(i);
            bullet.position.add(
                bullet.direction.x * BULLET_SPEED * deltaTime,
                bullet.direction.y * BULLET_SPEED * deltaTime
            );
            
            // Remove bullets that go off-screen
            if (bullet.position.x < 0 || bullet.position.x > WORLD_WIDTH ||
                bullet.position.y < 0 || bullet.position.y > WORLD_HEIGHT) {
                bullets.removeIndex(i);
            }
            
            // Debug info - print bullet position when it reaches the middle
            if (bullet.position.x > WORLD_WIDTH/2 - 10 && 
                bullet.position.x < WORLD_WIDTH/2 + 10 && 
                !bullet.isRedTeam) {
                Gdx.app.debug("Bullet", "Blue team bullet at middle: " + bullet.position);
            } else if (bullet.position.x > WORLD_WIDTH/2 - 10 && 
                       bullet.position.x < WORLD_WIDTH/2 + 10 && 
                       bullet.isRedTeam) {
                Gdx.app.debug("Bullet", "Red team bullet at middle: " + bullet.position);
            }
        }
    }
      private void checkCollisions() {
        // Check bullet collisions with players
        outerLoop:
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet bullet = bullets.get(i);
            Circle bulletCircle = new Circle(bullet.position, BULLET_RADIUS);
            
            for (Player player : players) {
                // Skip collision check if:
                // - Player is respawning 
                // - Bullet is from the same team as the player
                // - Game is over
                if (player.isRespawning || player.isRedTeam == bullet.isRedTeam || gameOver) {
                    continue;
                }
                
                // Simple rectangular collision
                Rectangle playerRect = new Rectangle(
                    player.position.x, player.position.y, 
                    STICKMAN_WIDTH, STICKMAN_HEIGHT
                );
                
                if (Intersector.overlaps(bulletCircle, playerRect)) {
                    // Hit detected!
                    player.health -= BULLET_DAMAGE;
                    
                    // Create hit effect (could be expanded to particle effects)
                    Gdx.app.debug("Combat", "Hit detected! " + 
                                 (bullet.isRedTeam ? "Red" : "Blue") + " team bullet hit " +
                                 (player.isRedTeam ? "Red" : "Blue") + " team player. Health: " + 
                                 player.health);
                    
                    // Check if player is defeated
                    if (player.health <= 0) {
                        // Update score
                        if (player.isRedTeam) {
                            blueTeamScore++;
                            Gdx.app.debug("Combat", "BLUE team scored a kill! Score: " + blueTeamScore);
                        } else {
                            redTeamScore++;
                            Gdx.app.debug("Combat", "RED team scored a kill! Score: " + redTeamScore);
                        }
                        
                        // Check victory condition immediately
                        if (redTeamScore >= SCORE_TO_WIN) {
                            gameOver = true;
                            winningTeam = "RED";
                            Gdx.app.debug("Game", "RED team wins with " + redTeamScore + " kills!");
                        } else if (blueTeamScore >= SCORE_TO_WIN) {
                            gameOver = true;
                            winningTeam = "BLUE";
                            Gdx.app.debug("Game", "BLUE team wins with " + blueTeamScore + " kills!");
                        }
                          // Start respawn timer
                        player.isRespawning = true;
                        player.respawnTimer = RESPAWN_TIME;
                        
                        // Randomize respawn position more in larger world
                        if (player.isRedTeam) {
                            // Red team respawns on the left side, away from the boundary
                            player.respawnX = MathUtils.random(100, WORLD_WIDTH/2 - 400);
                            player.respawnY = MathUtils.random(100, WORLD_HEIGHT - 100);
                            Gdx.app.debug("Respawn", "Red player respawning at " + player.respawnX + "," + player.respawnY);
                        } else {
                            // Blue team respawns on the right side, away from the boundary
                            player.respawnX = MathUtils.random(WORLD_WIDTH/2 + 400, WORLD_WIDTH - 200);
                            player.respawnY = MathUtils.random(100, WORLD_HEIGHT - 100);
                            Gdx.app.debug("Respawn", "Blue player respawning at " + player.respawnX + "," + player.respawnY);
                        }
                    }
                    
                    // Remove the bullet
                    bullets.removeIndex(i);
                    
                    // Continue outer loop (skip remaining players for this bullet)
                    continue outerLoop;
                }
            }
        }
        
        // Check bullet collisions with objects
        checkBulletObjectCollisions();
    }
      private void handlePlayerInput(float deltaTime) {
        if (localPlayer == null || localPlayer.isRespawning) return;
        
        // Movement
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            localPlayer.velocity.x = -MOVEMENT_SPEED;
            localPlayer.facingRight = false;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            localPlayer.velocity.x = MOVEMENT_SPEED;
            localPlayer.facingRight = true;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            localPlayer.velocity.y = MOVEMENT_SPEED;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            localPlayer.velocity.y = -MOVEMENT_SPEED;
        }
        
        // Handle shooting - check both SPACE and ENTER keys
        boolean spacePressed = Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
        boolean enterPressed = Gdx.input.isKeyJustPressed(Input.Keys.ENTER);
        
        // Shooting logic with cooldown
        if ((spacePressed || enterPressed) && shootCooldownTimer <= 0 && !gameOver) {
            Gdx.app.debug("Shooting", "Player firing! Key: " + 
                         (spacePressed ? "SPACE" : "ENTER") + 
                         ", Cooldown: " + shootCooldownTimer);
            
            // Create bullet and add directly to array for immediate feedback
            Vector2 bulletPos = new Vector2(
                localPlayer.position.x + STICKMAN_WIDTH / 2 + (localPlayer.facingRight ? 10 : -10),
                localPlayer.position.y + STICKMAN_HEIGHT - 20
            );
            Vector2 direction = new Vector2(localPlayer.facingRight ? 1 : -1, 0);
            Bullet newBullet = new Bullet(bulletPos, direction, localPlayer.isRedTeam);
            bullets.add(newBullet);
            
            // Set cooldown timers
            shootCooldownTimer = SHOOT_COOLDOWN;
            globalShootCooldownTimer = GLOBAL_SHOOT_COOLDOWN;
            
            Gdx.app.debug("Bullet Added", "New bullet count: " + bullets.size);
        }
        
        // Switch teams for testing (press T)
        if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            localPlayer.isRedTeam = !localPlayer.isRedTeam;
        }
        
        // Update shooting animation state for both keys
        localPlayer.isShooting = Gdx.input.isKeyPressed(Input.Keys.SPACE) || 
                                Gdx.input.isKeyPressed(Input.Keys.ENTER);
    }private void shoot(Player player) {
        if (player.isRespawning || globalShootCooldownTimer > 0) {
            Gdx.app.debug("Shooting", "Cannot shoot. Player is respawning or global cooldown active: " + globalShootCooldownTimer);
            return;
        }
        
        Vector2 bulletPos = new Vector2(
            player.position.x + STICKMAN_WIDTH / 2 + (player.facingRight ? 10 : -10),
            player.position.y + STICKMAN_HEIGHT - 20
        );
        
        Vector2 direction = new Vector2(player.facingRight ? 1 : -1, 0);
        
        // Make AI players randomly aim a bit up or down for more interesting shots
        if (player.isAI) {
            float randomY = MathUtils.random(-0.3f, 0.3f);
            direction.y = randomY;
            direction.nor(); // Normalize to keep speed consistent
        }
        
        // Create and add the bullet to the game
        Bullet newBullet = new Bullet(bulletPos, direction, player.isRedTeam);
        bullets.add(newBullet);
        
        // Set the global cooldown timer
        globalShootCooldownTimer = GLOBAL_SHOOT_COOLDOWN;

        // Debug message with more details
        Gdx.app.debug("Shooting", (player.isRedTeam ? "Red" : "Blue") + 
                     " team player shooting from " + bulletPos + " towards " + direction +
                     " | Bullet count: " + bullets.size);
    }
      private void updateAI(float deltaTime) {
        // Create a copy of the players array to avoid nested iteration issues
        Array<Player> playersCopy = new Array<>(players);

        // First, find and cache all health packs to avoid nested iteration
        Array<GameObject> healthPacks = new Array<>();
        for (GameObject obj : gameObjects) {
            if (obj.type == GameObjectType.HEALTH_PACK) {
                healthPacks.add(obj);
            }
        }

        // Advanced AI for non-player characters
        for (Player player : playersCopy) {
            if (player.isAI && !player.isRespawning) {                // Use role-based behavior for AI players
                executeRoleBasedBehavior(player, deltaTime);
                
                // Reset isShooting state for AI after a short time
                if (player.isShooting && MathUtils.randomBoolean(0.2f)) {
                    player.isShooting = false;
                }

                // Low health special case that overrides the role-based behavior
                if (player.health < 25) {
                    // Find nearest health pack using our cached list
                    GameObject nearestHealthPack = null;
                    float minDist = Float.MAX_VALUE;

                    for (GameObject healthPack : healthPacks) {
                        float dist = player.position.dst(healthPack.position);
                        if (dist < minDist) {
                            minDist = dist;
                            nearestHealthPack = healthPack;
                        }
                    }

                    if (nearestHealthPack != null) {
                        // Low health: seek health pack with higher priority
                        moveTowards(player, nearestHealthPack.position.x, nearestHealthPack.position.y);

                        // Debug log (less frequent to reduce spam)
                        if (gameTime % 2 < deltaTime) {
                            Gdx.app.debug("AI", (player.isRedTeam ? "Red" : "Blue") +
                                " AI seeking health pack at " + nearestHealthPack.position.x +
                                "," + nearestHealthPack.position.y);
                        }
                    }
                }

                // Apply some randomness to movement to make it less predictable
                if (MathUtils.randomBoolean(0.02f)) {
                    player.velocity.x += MathUtils.random(-MOVEMENT_SPEED * 0.3f, MOVEMENT_SPEED * 0.3f);
                }
                if (MathUtils.randomBoolean(0.02f)) {
                    player.velocity.y += MathUtils.random(-MOVEMENT_SPEED * 0.3f, MOVEMENT_SPEED * 0.3f);
                }

                // Limit velocity to prevent excessive speed
                if (player.velocity.len() > MOVEMENT_SPEED) {
                    player.velocity.setLength(MOVEMENT_SPEED);
                }
            }
        }
    }
    
    // Helper to find nearest enemy for AI targeting
    private Player findNearestEnemy(Player player) {
        Player nearest = null;
        float minDist = Float.MAX_VALUE;
        
        for (Player other : players) {
            if (other.isRedTeam != player.isRedTeam && !other.isRespawning) {
                float dist = player.position.dst(other.position);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = other;
                }
            }
        }
        
        return nearest;
    }    
    /* Helper method removed to avoid nested iteration
    private GameObject findNearestHealthPack(Player player) {
        GameObject nearest = null;
        float minDist = Float.MAX_VALUE;
        
        for (GameObject obj : gameObjects) {
            if (obj.type == GameObjectType.HEALTH_PACK) {
                float dist = player.position.dst(obj.position);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = obj;
                }
            }
        }
        
        return nearest;
    } */
    
    // AI helper to move toward a target
    private void moveTowards(Player player, float targetX, float targetY) {
        float dx = targetX - player.position.x;
        float dy = targetY - player.position.y;
        Vector2 direction = new Vector2(dx, dy).nor();
        
        player.velocity.x = direction.x * MOVEMENT_SPEED;
        player.velocity.y = direction.y * MOVEMENT_SPEED;
        
        // Update facing direction based on movement
        if (Math.abs(direction.x) > 0.1f) {
            player.facingRight = direction.x > 0;
        }
    }
    
    // AI helper to move away from a target
    private void moveAway(Player player, float targetX, float targetY, float speedFactor) {
        float dx = player.position.x - targetX;
        float dy = player.position.y - targetY;
        Vector2 direction = new Vector2(dx, dy).nor();
        
        player.velocity.x = direction.x * MOVEMENT_SPEED * speedFactor;
        player.velocity.y = direction.y * MOVEMENT_SPEED * speedFactor;
    }
    
    // AI logic for patrolling territory
    private void patrolTerritory(Player player) {
        float centerX = player.isRedTeam ? WORLD_WIDTH * 0.25f : WORLD_WIDTH * 0.75f;
        float areaSize = WORLD_WIDTH * 0.4f; // Size of patrol area
        
        // If player is too far from team territory center, move back
        float distFromCenter = Math.abs(player.position.x - centerX);
        
        if (distFromCenter > areaSize) {
            moveTowards(player, centerX, player.position.y);
        } else if (MathUtils.randomBoolean(0.02f)) {
            // Random movement within territory
            float randomX = centerX + MathUtils.random(-areaSize, areaSize);
            float randomY = MathUtils.random(100, WORLD_HEIGHT - 100);
            moveTowards(player, randomX, randomY);
        }
    }
    
    // Assign AI roles for better team tactics
    private void assignTeamRoles() {
        // Count active players on each team
        int activeRedPlayers = 0;
        int activeBluePlayers = 0;
        
        for (Player player : players) {
            if (!player.isRespawning) {
                if (player.isRedTeam) activeRedPlayers++;
                else activeBluePlayers++;
            }
        }
        
        // Reset all roles first
        int redAttackers = 0;
        int redDefenders = 0;
        int blueAttackers = 0;
        int blueDefenders = 0;
        
        // Assign roles based on active players
        for (Player player : players) {
            if (player.isAI && !player.isRespawning) {
                if (player.isRedTeam) {
                    // Red team roles
                    if (redDefenders < activeRedPlayers / 3 + 1) {
                        // Defender
                        player.aiRole = AIRole.DEFENDER;
                        redDefenders++;
                    } else {
                        // Attacker
                        player.aiRole = AIRole.ATTACKER;
                        redAttackers++;
                    }
                } else {
                    // Blue team roles
                    if (blueDefenders < activeBluePlayers / 3 + 1) {
                        // Defender
                        player.aiRole = AIRole.DEFENDER;
                        blueDefenders++;
                    } else {
                        // Attacker
                        player.aiRole = AIRole.ATTACKER;
                        blueAttackers++;
                    }
                }
            }
        }
        
        Gdx.app.debug("AI Roles", "Red: " + redAttackers + " attackers, " + 
                     redDefenders + " defenders. Blue: " + blueAttackers + 
                     " attackers, " + blueDefenders + " defenders");
    }    // AI behavior based on assigned role
    private void executeRoleBasedBehavior(Player player, float deltaTime) {
        Player target = findClosestEnemy(player);
        if (target != null) {
            float distance = player.position.dst(target.position);

            // Update facing direction based on target position
            player.facingRight = target.position.x > player.position.x;

            // If within shooting range, shoot with increased probability
            if (distance <= MAX_AI_SIGHT_RANGE / 2) {
                // More aggressive shooting - higher chance for blue team
                float shootChance = player.isRedTeam ? 0.03f : 0.05f;
                
                if (MathUtils.randomBoolean(shootChance)) {
                    Gdx.app.debug("AI Shooting", (player.isRedTeam ? "Red" : "Blue") + 
                                 " team AI shooting at " + 
                                 (target.isRedTeam ? "Red" : "Blue") + " team");
                    
                    // Directly create and add bullet for AI
                    if (globalShootCooldownTimer <= 0 && !player.isRespawning) {
                        // Calculate bullet position
                        Vector2 bulletPos = new Vector2(
                            player.position.x + STICKMAN_WIDTH / 2 + (player.facingRight ? 10 : -10),
                            player.position.y + STICKMAN_HEIGHT - 20
                        );
                        
                        // Calculate bullet direction with some randomness for AI
                        Vector2 direction = new Vector2(player.facingRight ? 1 : -1, 0);
                        float randomY = MathUtils.random(-0.3f, 0.3f);
                        direction.y = randomY;
                        direction.nor(); // Normalize to keep speed consistent
                        
                        // Create and add bullet directly
                        Bullet newBullet = new Bullet(bulletPos, direction, player.isRedTeam);
                        bullets.add(newBullet);
                        
                        // Set global cooldown
                        globalShootCooldownTimer = GLOBAL_SHOOT_COOLDOWN;
                        
                        player.isShooting = true;
                    }
                }
            } else {
                // Otherwise, chase the target
                Vector2 direction = new Vector2(target.position).sub(player.position).nor();
                player.velocity.set(direction.scl(MOVEMENT_SPEED));
            }
        }
    }

    // Helper method to find the closest enemy
    private Player findClosestEnemy(Player player) {
        Player closest = null;
        float closestDistance = Float.MAX_VALUE;

        for (Player other : players) {
            if (other.isRedTeam != player.isRedTeam && !other.isRespawning) {
                float distance = player.position.dst(other.position);
                if (distance < closestDistance) {
                    closest = other;
                    closestDistance = distance;
                }
            }
        }
        return closest;
    }
    
    // AI helper to patrol defensive position
    private void patrolDefensivePosition(Player player) {
        // Defenders stay close to the team's boundary
        float boundaryX = WORLD_WIDTH / 2;
        float patrolDistance = 300;  // Distance from boundary to patrol
        
        float minX, maxX;
        if (player.isRedTeam) {
            // Red team defends left side
            minX = boundaryX - patrolDistance;
            maxX = boundaryX - 50;
        } else {
            // Blue team defends right side
            minX = boundaryX + 50;
            maxX = boundaryX + patrolDistance;
        }
        
        // If player is outside patrol zone, move back to it
        if (player.position.x < minX || player.position.x > maxX) {
            float targetX = (minX + maxX) / 2;
            moveTowards(player, targetX, player.position.y);
        } 
        // Otherwise, do some random movement within zone
        else if (MathUtils.randomBoolean(0.01f)) {
            float randomX = MathUtils.random(minX, maxX);
            float randomY = MathUtils.random(100, WORLD_HEIGHT - 100);
            moveTowards(player, randomX, randomY);
        }
        
        // Occasionally shoot while patrolling
        if (MathUtils.randomBoolean(0.005f)) {
            shoot(player);
        }
    }
      private void updateCamera() {
        // Set default position in case player is respawning
        if (localPlayer == null || localPlayer.isRespawning) {
            camera.position.set(WORLD_WIDTH / 2, WORLD_HEIGHT / 2, 0);
            return;
        }
        
        // Follow the local player with slight smoothing
        float targetX = localPlayer.position.x + STICKMAN_WIDTH/2;
        float targetY = localPlayer.position.y + STICKMAN_HEIGHT/2;
        
        // Center camera on player with smoothing
        float lerp = 0.1f; // Increased for faster camera movement in large world
        camera.position.x += (targetX - camera.position.x) * lerp;
        camera.position.y += (targetY - camera.position.y) * lerp;
        
        // Keep camera in world bounds with smaller margins relative to the large world
        float margin = 50; // Reduced margin
        float camHalfWidth = viewport.getWorldWidth() * 0.5f;
        float camHalfHeight = viewport.getWorldHeight() * 0.5f;
        
        camera.position.x = MathUtils.clamp(camera.position.x, 
                                         camHalfWidth + margin, 
                                         WORLD_WIDTH - camHalfWidth - margin);
        camera.position.y = MathUtils.clamp(camera.position.y, 
                                         camHalfHeight + margin, 
                                         WORLD_HEIGHT - camHalfHeight - margin);
            
        // Log camera position for debugging
        if (gameTime % 5 < 0.1f) { // Log only occasionally
            Gdx.app.debug("Camera", "Position: " + camera.position.x + ", " + camera.position.y +
                        " Viewport size: " + viewport.getWorldWidth() + "x" + viewport.getWorldHeight());
        }
    }    @Override
    public void resize(int width, int height) {
        // Update viewport with the new window size
        viewport.update(width, height, false);
        
        // Log viewport information after resize
        Gdx.app.debug("Resize", "Window resized to " + width + "x" + height + 
                   " Viewport world size: " + viewport.getWorldWidth() + "x" + viewport.getWorldHeight());
        
        // Don't reset camera position to center on resize - maintain player view
        if (localPlayer != null && !localPlayer.isRespawning) {
            // Ensure camera is near the player after resize
            updateCamera();
        }
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        batch.dispose();
        font.dispose();
    }

    /**
     * Represents a player in the game
     */    private static class Player {
        Vector2 position;
        Vector2 velocity;
        boolean isRedTeam;
        boolean isAI;
        boolean isShooting;
        boolean facingRight;
        int health = 100;
        boolean isRespawning = false;
        float respawnTimer = 0;
        float respawnX = 0;  // X position to respawn at
        float respawnY = 0;  // Y position to respawn at
        AIRole aiRole = AIRole.NONE; // AI role for team tactics
        
        Player(float x, float y, boolean isRedTeam) {
            this.position = new Vector2(x, y);
            this.velocity = new Vector2(0, 0);
            this.isRedTeam = isRedTeam;
            this.facingRight = isRedTeam;
            
            // Initialize default respawn position
            this.respawnX = x;
            this.respawnY = y;
        }
    }
    
    /**
     * Represents a bullet in the game
     */
    private static class Bullet {
        Vector2 position;
        Vector2 direction;
        boolean isRedTeam;
        
        Bullet(Vector2 position, Vector2 direction, boolean isRedTeam) {
            this.position = position.cpy();
            this.direction = direction.cpy();
            this.isRedTeam = isRedTeam;
        }
    }
      /**
     * Represents static game objects like platforms, obstacles, etc.
     */
    private static class GameObject {
        Vector2 position;
        float width, height;
        Color color;
        GameObjectType type;
        Object userData; // For linking objects (like teleporters) or storing additional data
        float effectTimer = 0; // For animations like pulsing health packs
        
        GameObject(float x, float y, float width, float height, Color color, GameObjectType type) {
            this.position = new Vector2(x, y);
            this.width = width;
            this.height = height;
            this.color = color;
            this.type = type;
        }
        
        Rectangle getBounds() {
            return new Rectangle(position.x, position.y, width, height);
        }
        
        // Update any time-based effects on the object
        void update(float deltaTime) {
            effectTimer += deltaTime;
            
            // Reset timer to prevent float overflow on very long games
            if (effectTimer > 1000) {
                effectTimer = 0;
            }
        }
    }
      private enum GameObjectType {
        PLATFORM,
        OBSTACLE,
        COVER,
        BARRIER,
        HEALTH_PACK,
        TELEPORTER
    }
    
    // Game configuration
    private static final int SCORE_TO_WIN = 20;
    private boolean gameOver = false;
    private String winningTeam = "";
    private float gameOverMessageTime = 0;
    
    // AI role enum for team tactics
    private enum AIRole {
        NONE,
        ATTACKER,
        DEFENDER
    }
    
    private void drawMinimap() {
        // Calculate minimap position and size
        float minimapSize = 150;
        float minimapX = camera.position.x + viewport.getWorldWidth()/2 - minimapSize - 10;
        float minimapY = camera.position.y + viewport.getWorldHeight()/2 - minimapSize - 10;
        float minimapScale = minimapSize / WORLD_WIDTH;
        
        // Draw minimap background
        shapeRenderer.begin(ShapeType.Filled);
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 0.7f);
        shapeRenderer.rect(minimapX, minimapY, minimapSize, minimapSize * (WORLD_HEIGHT / WORLD_WIDTH));

        // Draw team territories
        shapeRenderer.setColor(0.5f, 0.1f, 0.1f, 0.5f);
        shapeRenderer.rect(minimapX, minimapY, minimapSize/2, minimapSize * (WORLD_HEIGHT / WORLD_WIDTH));
        
        shapeRenderer.setColor(0.1f, 0.1f, 0.5f, 0.5f);
        shapeRenderer.rect(minimapX + minimapSize/2, minimapY, minimapSize/2, minimapSize * (WORLD_HEIGHT / WORLD_WIDTH));
        
        // Draw dividing line
        shapeRenderer.setColor(1f, 1f, 1f, 0.5f);
        shapeRenderer.rectLine(
            minimapX + minimapSize/2, 
            minimapY, 
            minimapX + minimapSize/2, 
            minimapY + minimapSize * (WORLD_HEIGHT / WORLD_WIDTH), 
            1);

        // Draw players on minimap
        for (Player player : players) {
            if (!player.isRespawning) {
                // Set color based on team
                if (player.isRedTeam) {
                    shapeRenderer.setColor(1f, 0.2f, 0.2f, 1f);
                } else {
                    shapeRenderer.setColor(0.2f, 0.2f, 1f, 1f);
                }
                
                // Draw player as a dot
                float playerX = minimapX + player.position.x * minimapScale;
                float playerY = minimapY + player.position.y * minimapScale;
                
                // Highlight local player with a bigger dot
                float dotSize = (player == localPlayer) ? 4f : 2f;
                shapeRenderer.circle(playerX, playerY, dotSize);
            }
        }
        
        // Draw viewport rectangle to show current view area
        shapeRenderer.setColor(1f, 1f, 1f, 0.3f);
        float viewX = minimapX + (camera.position.x - viewport.getWorldWidth()/2) * minimapScale;
        float viewY = minimapY + (camera.position.y - viewport.getWorldHeight()/2) * minimapScale;
        float viewWidth = viewport.getWorldWidth() * minimapScale;
        float viewHeight = viewport.getWorldHeight() * minimapScale;
        shapeRenderer.rect(viewX, viewY, viewWidth, viewHeight);
        
        shapeRenderer.end();

        // Draw minimap border
        shapeRenderer.begin(ShapeType.Line);
        shapeRenderer.setColor(0.8f, 0.8f, 0.8f, 0.8f);
        shapeRenderer.rect(minimapX, minimapY, minimapSize, minimapSize * (WORLD_HEIGHT / WORLD_WIDTH));
        shapeRenderer.end();
    }
}
