import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

public class GitHubAutoDownloader {
    public static void main(String[] args) {
        printBanner();

        if (args.length != 1) {
            System.out.println("[❗] Sử dụng: java -jar gh-downloader.jar <github_repo_url>");
            return;
        }

        String repoUrl = args[0].trim();
        if (!isValidRepoUrl(repoUrl)) {
            System.out.println("[❌] URL không hợp lệ. Phải là link GitHub repo hoặc trang hỗ trợ cấu trúc giống GitHub.");
            return;
        }

        try {
            String[] repoInfo = extractRepoInfo(repoUrl);
            String owner = repoInfo[0], repo = repoInfo[1];
            String apiUrl = buildApiUrl(repoUrl, owner, repo);

            System.out.println("[🔍] Kiểm tra release mới nhất cho: " + owner + "/" + repo);

            String json = readURL(apiUrl);
            JSONObject release = new JSONObject(json);
            JSONArray assets = release.getJSONArray("assets");

            if (assets.length() == 0) {
                System.out.println("[ℹ️] Không có file nào trong release.");
                return;
            }

            System.out.println("[✅] Release có " + assets.length() + " file:");
            
            if (assets.length() == 1) {
                JSONObject asset = assets.getJSONObject(0);
                downloadAsset(asset, ".");
            } else {
                File folder = new File(repo);
                if (!folder.exists()) folder.mkdirs();

                // Display file options
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    System.out.println("(" + (i + 1) + ") - " + asset.getString("name"));
                }
                System.out.println("(" + (assets.length() + 1) + ") - Chọn nhiều file");
                System.out.println("(" + (assets.length() + 2) + ") - Tải tất cả file");

                // Get user input
                Scanner scanner = new Scanner(System.in);
                System.out.print("Chọn số: ");
                String input = scanner.nextLine().trim();

                if (input.equals(String.valueOf(assets.length() + 2))) {
                    // Download all files
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        downloadAsset(asset, repo);
                    }
                } else if (input.equals(String.valueOf(assets.length() + 1))) {
                    // Select multiple files
                    System.out.print("Nhập các số (cách nhau bằng dấu cách): ");
                    String[] choices = scanner.nextLine().trim().split("\\s+");
                    for (String choice : choices) {
                        try {
                            int index = Integer.parseInt(choice) - 1;
                            if (index >= 0 && index < assets.length()) {
                                JSONObject asset = assets.getJSONObject(index);
                                downloadAsset(asset, repo);
                            } else {
                                System.out.println("[⚠️] Số " + choice + " không hợp lệ, bỏ qua.");
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("[⚠️] Đầu vào không phải số: " + choice + ", bỏ qua.");
                        }
                    }
                } else {
                    // Download single file
                    try {
                        int index = Integer.parseInt(input) - 1;
                        if (index >= 0 && index < assets.length()) {
                            JSONObject asset = assets.getJSONObject(index);
                            downloadAsset(asset, repo);
                        } else {
                            System.out.println("[⚠️] Số không hợp lệ.");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("[⚠️] Đầu vào không phải số.");
                        return;
                    }
                }
            }

            System.out.println("[🎉] Hoàn tất tải release!");

        } catch (IOException e) {
            System.out.println("[💥] Lỗi kết nối: " + e.getMessage());
        } catch (JSONException e) {
            System.out.println("[💥] Không tìm thấy release hợp lệ hoặc JSON lỗi.");
        }
    }

    static void printBanner() {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║     🚀 GitHub Release Downloader       ║");
        System.out.println("╚════════════════════════════════════════╝\n");
    }

    static boolean isValidRepoUrl(String url) {
        return url.matches("^https://[^/]+/[^/]+/[^/]+/?$");
    }

    static String[] extractRepoInfo(String url) {
        String[] parts = url.replace("https://", "").split("/");
        return new String[]{parts[1], parts[2]};
    }

    static String buildApiUrl(String baseUrl, String owner, String repo) {
        if (baseUrl.contains("github.com"))
            return "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
        else
            return baseUrl + "/releases/latest"; // Giả định git server custom có cùng cấu trúc
    }

    static void downloadAsset(JSONObject asset, String dir) throws IOException {
        String name = asset.getString("name");
        String url = asset.getString("browser_download_url");
        System.out.println("  ⬇️  " + name);

        URL downloadUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();
        conn.setRequestProperty("User-Agent", "gh-downloader");

        try (
            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(dir + File.separator + name)
        ) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1)
                out.write(buffer, 0, len);
        }
    }

    static String readURL(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setRequestProperty("User-Agent", "gh-downloader");

        int response = conn.getResponseCode();
        if (response >= 400) throw new IOException("HTTP lỗi: " + response);

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null)
            data.append(line);
        return data.toString();
    }
}
