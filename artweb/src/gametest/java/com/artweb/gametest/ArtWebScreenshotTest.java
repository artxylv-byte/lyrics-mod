package com.artweb.gametest;

import com.artweb.ArtWeb;
import com.artweb.block.WebScreenBlock;
import com.artweb.block.WebScreenBlockEntity;
import com.artweb.client.PageViews;
import com.artweb.client.gui.BrowserScreen;
import com.artweb.client.page.PageCache;
import com.artweb.client.page.PageLayout;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Serves a test website from inside the game, puts it on a Web Screen and screenshots the screen in the world and
 * the browser window, then clicks a link. Screenshots land in {@code build/run/clientGameTest/screenshots}.
 */
public class ArtWebScreenshotTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("ArtWeb-GameTest");

	private static final String HOME = """
			<!doctype html><html><head><title>Block Builders Weekly</title><style>body{font:14px sans-serif}</style>
			<script>console.log('not run')</script></head><body>
			<nav><a href="/">Home</a> | <a href="/second">Tips</a></nav>
			<h1>Block Builders Weekly</h1>
			<p>Welcome to the <b>test site</b> for ArtWeb. This page is served from inside the game so the screen
			can be checked without the internet. Read the <a href="/second">building tips</a> next.</p>
			<img src="/sunset.png" alt="A sunset" width="320" height="160">
			<h2>This week</h2>
			<ul><li>A castle with <i>four</i> towers</li><li>Redstone doors that open by themselves</li>
			<li>Gardens with <code>flowering_azalea</code></li></ul>
			<blockquote>Every great build starts with a single block.</blockquote>
			<h3>Command of the week</h3>
			<pre>/fill ~ ~ ~ ~10 ~ ~10 stone
/setblock ~ ~1 ~ torch</pre>
			<ol><li>Pick a spot</li><li>Build the walls</li><li>Add a roof</li></ol>
			<hr><p>Made for testing. More text follows so the page needs scrolling. Lorem ipsum dolor sit amet,
			consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.</p>
			</body></html>""";

	private static final String SECOND = """
			<html><head><title>Building tips</title></head><body><h1>Building tips</h1>
			<p>Use <b>contrast</b>: dark wood next to light stone. Give roofs an <a href="/">overhang</a>.</p>
			<h2>Lighting</h2><p>Hide light sources under carpets or behind leaves.</p></body></html>""";

	@Override
	public void runTest(ClientGameTestContext context) {
		HttpServer server;
		try {
			server = startServer();
		} catch (IOException e) {
			throw new AssertionError("Couldn't start the test web server", e);
		}
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		try {
			run(context, base);
		} finally {
			server.stop(0);
		}
	}

	private void run(ClientGameTestContext context, String base) {
		context.waitFor(client -> client.getOverlay() == null, 20 * 120);
		context.runOnClient(client -> client.options.renderDistance().set(5));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			try {
				world.getClientWorld().waitForChunksRender(20 * 60 * 5);
			} catch (AssertionError e) {
				LOGGER.warn("Chunks still rendering after 5 minutes; taking screenshots anyway");
			}
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamerule doDaylightCycle false");

			// A small stage high above the ground: the player looks south at a 4 × 3 screen.
			BlockPos screenPos = new BlockPos(2, 101, 4);
			world.getServer().runCommand("fill -6 100 -3 6 100 9 minecraft:polished_andesite");
			world.getServer().runCommand("fill -6 101 -3 6 108 9 minecraft:air");
			world.getServer().runCommand("fill -6 101 7 6 105 7 minecraft:bookshelf");
			world.getServer().runCommand("tp @p 0.5 101 0.5 0 0");
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				level.setBlockAndUpdate(screenPos, ArtWeb.WEB_SCREEN.defaultBlockState().setValue(WebScreenBlock.FACING, Direction.NORTH));
				if (level.getBlockEntity(screenPos) instanceof WebScreenBlockEntity screen) {
					screen.update(base + "/", 4, 3, 0);
				} else {
					throw new AssertionError("No block entity at the screen");
				}
			});
			String home = base + "/";
			context.waitFor(client -> client.level.getBlockEntity(screenPos) instanceof WebScreenBlockEntity s && s.url().equals(home), 200);
			context.waitFor(client -> PageCache.get(home).state() != PageCache.State.LOADING, 20 * 30);
			context.runOnClient(client -> {
				PageCache.Entry e = PageCache.get(home);
				if (e.state() != PageCache.State.READY) {
					throw new AssertionError("Test page didn't load: " + e.error());
				}
			});
			context.waitTicks(40); // picture download
			context.takeScreenshot("01-screen-in-world");

			world.getServer().runOnServer(server -> {
				if (server.overworld().getBlockEntity(screenPos) instanceof WebScreenBlockEntity screen) {
					screen.update(home, 4, 3, 150);
				}
			});
			context.waitTicks(10);
			context.takeScreenshot("02-screen-scrolled");

			// The browser window, showing exactly what the block shows.
			context.setScreen(() -> new BrowserScreen(screenPos, (WebScreenBlockEntity) net.minecraft.client.Minecraft.getInstance().level.getBlockEntity(screenPos)));
			context.waitTicks(10);
			context.takeScreenshot("03-browser-window");

			// Scroll back to the top with the Home key, then click the "building tips" link.
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_HOME);
			context.waitTicks(5);
			double[] at = context.computeOnClient(client -> {
				BrowserScreen screen = (BrowserScreen) client.screen;
				PageLayout.Result layout = PageViews.get(home, 4 * PageViews.PX_PER_BLOCK, client.font).layout();
				PageLayout.Link link = layout.links().stream().filter(l -> l.href().endsWith("/second")).skip(1).findFirst()
						.orElseThrow(() -> new AssertionError("link not found"));
				double[] gui = screen.pageToScreen(link.x() + link.w() / 2, link.y() + link.h() / 2);
				double scale = client.getWindow().getGuiScale();
				return new double[] {gui[0] * scale, gui[1] * scale};
			});
			context.getInput().setCursorPos(at[0], at[1]);
			context.waitTicks(2);
			context.takeScreenshot("04-browser-hover-link");
			context.getInput().pressMouse(0);
			String second = base + "/second";
			context.waitFor(client -> client.level.getBlockEntity(screenPos) instanceof WebScreenBlockEntity s && s.url().equals(second), 100);
			context.waitFor(client -> PageCache.get(second).state() != PageCache.State.LOADING, 20 * 30);
			context.waitTicks(5);
			context.takeScreenshot("05-browser-after-click");

			context.setScreen(() -> null);
			context.waitTicks(10);
			context.takeScreenshot("06-world-after-click");

			// Right-clicking the picture (here two blocks away from the screen block) opens the window too.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitFor(client -> client.screen instanceof BrowserScreen, 40);
			context.setScreen(() -> null);
			context.waitTicks(5);

			// An address that can't load shows a message instead of a blank screen.
			world.getServer().runOnServer(server -> {
				if (server.overworld().getBlockEntity(screenPos) instanceof WebScreenBlockEntity screen) {
					screen.update("http://127.0.0.1:1/", 2, 1, 0);
				}
			});
			context.waitFor(client -> PageCache.get("http://127.0.0.1:1/").state() == PageCache.State.ERROR, 20 * 30);
			context.waitTicks(5);
			context.takeScreenshot("07-error-message");
		}
	}

	private static HttpServer startServer() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			String path = ex.getRequestURI().getPath();
			byte[] body;
			String type;
			switch (path) {
				case "/sunset.png" -> {
					body = sunset();
					type = "image/png";
				}
				case "/second" -> {
					body = SECOND.getBytes(StandardCharsets.UTF_8);
					type = "text/html; charset=utf-8";
				}
				default -> {
					body = HOME.getBytes(StandardCharsets.UTF_8);
					type = "text/html; charset=utf-8";
				}
			}
			ex.getResponseHeaders().add("Content-Type", type);
			ex.sendResponseHeaders(200, body.length);
			try (OutputStream out = ex.getResponseBody()) {
				out.write(body);
			}
		});
		server.start();
		return server;
	}

	/** A small generated picture: sky gradient, sun and hills. */
	private static byte[] sunset() throws IOException {
		BufferedImage img = new BufferedImage(320, 160, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setPaint(new GradientPaint(0, 0, new Color(0x2B1E5C), 0, 160, new Color(0xFF8A50)));
		g.fillRect(0, 0, 320, 160);
		g.setColor(new Color(0xFFD27A));
		g.fillOval(130, 70, 60, 60);
		g.setColor(new Color(0x2E4A2E));
		g.fillOval(-60, 110, 260, 120);
		g.setColor(new Color(0x3C5E35));
		g.fillOval(120, 120, 280, 120);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return out.toByteArray();
	}
}
