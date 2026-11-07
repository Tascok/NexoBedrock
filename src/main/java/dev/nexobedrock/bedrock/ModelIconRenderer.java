package dev.nexobedrock.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexobedrock.pack.ResolvedModel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Renders the Java GUI display of a cuboid model into an inventory sprite. */
public final class ModelIconRenderer {
    private static final int SIZE = 128;
    private static final int TRANSPARENT = 0;

    public byte[] render(ResolvedModel model, AtlasResult atlas) throws IOException {
        BufferedImage texture = ImageIO.read(new ByteArrayInputStream(atlas.png()));
        if (texture == null) {
            throw new IOException("Atlas 3D invalido para icone");
        }
        JsonObject gui = object(model.display(), "gui");
        float[] rotation = values(gui, "rotation", new float[]{30, 225, 0});
        float[] scale = values(gui, "scale", new float[]{1, 1, 1});
        float[] translation = values(gui, "translation", new float[]{0, 0, 0});
        List<Quad> quads = new ArrayList<>();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        JsonArray elements = model.elements();
        for (int index = 0; index < elements.size(); index++) {
            if (!elements.get(index).isJsonObject()) continue;
            JsonObject element = elements.get(index).getAsJsonObject();
            float[] from = values(element, "from", null);
            float[] to = values(element, "to", null);
            JsonObject faces = object(element, "faces");
            if (from == null || to == null || faces == null) continue;
            float x0 = from[0], y0 = from[1], z0 = from[2];
            float x1 = to[0], y1 = to[1], z1 = to[2];
            for (String name : TextureAtlasBuilder.FACE_NAMES) {
                if (!faces.has(name)) continue;
                UvRect uv = atlas.uvs().get(new FaceKey(index, name));
                if (uv == null) continue;
                float[][] corners = switch (name) {
                    case "north" -> new float[][]{{x1,y1,z0},{x0,y1,z0},{x0,y0,z0},{x1,y0,z0}};
                    case "south" -> new float[][]{{x0,y1,z1},{x1,y1,z1},{x1,y0,z1},{x0,y0,z1}};
                    case "east" -> new float[][]{{x1,y1,z1},{x1,y1,z0},{x1,y0,z0},{x1,y0,z1}};
                    case "west" -> new float[][]{{x0,y1,z0},{x0,y1,z1},{x0,y0,z1},{x0,y0,z0}};
                    case "up" -> new float[][]{{x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}};
                    default -> new float[][]{{x0,y0,z1},{x1,y0,z1},{x1,y0,z0},{x0,y0,z0}};
                };
                Point[] points = new Point[4];
                JsonObject elementRotation = object(element, "rotation");
                float[] pivot = values(elementRotation, "origin", new float[]{8,8,8});
                float angle = scalar(elementRotation, "angle");
                String axis = elementRotation == null || !elementRotation.has("axis") ? "" : elementRotation.get("axis").getAsString();
                for (int i = 0; i < 4; i++) {
                    float[] position = corners[i].clone();
                    rotate(position, pivot, axis, angle);
                    for (int a = 0; a < 3; a++) position[a] = (position[a] - 8) * scale[a] + translation[a];
                    rotate(position, new float[3], "x", rotation[0]);
                    rotate(position, new float[3], "y", rotation[1]);
                    rotate(position, new float[3], "z", rotation[2]);
                    points[i] = new Point(position[0], -position[1], position[2]);
                    minX = Math.min(minX, points[i].x); maxX = Math.max(maxX, points[i].x);
                    minY = Math.min(minY, points[i].y); maxY = Math.max(maxY, points[i].y);
                }
                quads.add(new Quad(points, uv, shade(name)));
            }
        }
        if (quads.isEmpty()) throw new IOException("Modelo 3D sem faces renderizaveis para icone");
        float factor = Math.min((SIZE - 12f) / Math.max(1f, maxX - minX),
                (SIZE - 12f) / Math.max(1f, maxY - minY));
        float midX = (minX + maxX) / 2, midY = (minY + maxY) / 2;
        BufferedImage result = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        float[] depth = new float[SIZE * SIZE];
        Arrays.fill(depth, Float.NEGATIVE_INFINITY);
        for (Quad quad : quads) {
            Point[] p = quad.corners;
            float[] u = {quad.uv.u(), quad.uv.u() + quad.uv.width()};
            float[] v = {quad.uv.v(), quad.uv.v() + quad.uv.height()};
            Vertex a = vertex(p[0], u[0], v[0], factor, midX, midY);
            Vertex b = vertex(p[1], u[1], v[0], factor, midX, midY);
            Vertex c = vertex(p[2], u[1], v[1], factor, midX, midY);
            Vertex d = vertex(p[3], u[0], v[1], factor, midX, midY);
            draw(result, depth, texture, a, b, c, quad.shade);
            draw(result, depth, texture, a, c, d, quad.shade);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(result, "png", output);
        return output.toByteArray();
    }

    private Vertex vertex(Point p, float u, float v, float factor, float midX, float midY) {
        return new Vertex((p.x - midX) * factor + SIZE / 2f,
                (p.y - midY) * factor + SIZE / 2f, p.z, u, v);
    }

    private void draw(BufferedImage image, float[] depth, BufferedImage texture,
                      Vertex a, Vertex b, Vertex c, float shade) {
        float denominator = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y);
        if (Math.abs(denominator) < 0.00001f) return;
        int left = Math.max(0, (int)Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
        int right = Math.min(SIZE - 1, (int)Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
        int top = Math.max(0, (int)Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
        int bottom = Math.min(SIZE - 1, (int)Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));
        for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
            float w0 = ((b.y-c.y)*(x+.5f-c.x)+(c.x-b.x)*(y+.5f-c.y))/denominator;
            float w1 = ((c.y-a.y)*(x+.5f-c.x)+(a.x-c.x)*(y+.5f-c.y))/denominator;
            float w2 = 1-w0-w1;
            if (w0 < -0.0001f || w1 < -0.0001f || w2 < -0.0001f) continue;
            float z = a.z*w0+b.z*w1+c.z*w2;
            int offset = y*SIZE+x;
            if (z <= depth[offset]) continue;
            int tx = Math.max(0, Math.min(texture.getWidth()-1,
                    (int)(a.u*w0+b.u*w1+c.u*w2)));
            int ty = Math.max(0, Math.min(texture.getHeight()-1,
                    (int)(a.v*w0+b.v*w1+c.v*w2)));
            int argb = texture.getRGB(tx,ty);
            int alpha = argb >>> 24;
            if (alpha == 0) continue;
            int red = Math.round(((argb >>> 16) & 255) * shade);
            int green = Math.round(((argb >>> 8) & 255) * shade);
            int blue = Math.round((argb & 255) * shade);
            image.setRGB(x,y,(alpha << 24)|(red << 16)|(green << 8)|blue);
            depth[offset] = z;
        }
    }

    private float shade(String face) {
        return switch (face) {
            case "up" -> 1f;
            case "north", "south" -> .85f;
            case "east", "west" -> .72f;
            default -> .6f;
        };
    }

    private void rotate(float[] p, float[] pivot, String axis, float degrees) {
        double a = Math.toRadians(degrees);
        float c = (float)Math.cos(a), s = (float)Math.sin(a);
        float x = p[0] - pivot[0], y = p[1] - pivot[1], z = p[2] - pivot[2];
        switch (axis) {
            case "x" -> { p[1] = pivot[1] + c*y-s*z; p[2] = pivot[2] + s*y+c*z; }
            case "y" -> { p[0] = pivot[0] + c*x+s*z; p[2] = pivot[2] - s*x+c*z; }
            case "z" -> { p[0] = pivot[0] + c*x-s*y; p[1] = pivot[1] + s*x+c*y; }
        }
    }

    private JsonObject object(JsonObject root, String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : null;
    }

    private float scalar(JsonObject root, String key) {
        return root != null && root.has(key) ? root.get(key).getAsFloat() : 0f;
    }

    private float[] values(JsonObject root, String key, float[] fallback) {
        if (root == null || !root.has(key) || !root.get(key).isJsonArray()) return fallback;
        JsonArray a = root.getAsJsonArray(key);
        if (a.size() < 3) return fallback;
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private record Point(float x, float y, float z) {}
    private record Vertex(float x, float y, float z, float u, float v) {}
    private record Quad(Point[] corners, UvRect uv, float shade) {}
}
