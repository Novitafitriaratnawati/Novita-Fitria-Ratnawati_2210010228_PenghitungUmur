package aplikasiperhitunganumur2;
import javax.swing.JTextArea;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.Period;
import java.util.function.Supplier;
import org.json.JSONArray;
import org.json.JSONObject;

public class PenghitungUmurHelper {

    private Thread peristiwaThread;
    private volatile boolean stopFetching;

    // Menghitung detail umur dalam tahun, bulan, dan hari
    public String hitungUmurDetail(LocalDate lahir, LocalDate sekarang) {
        Period period = Period.between(lahir, sekarang);
        return period.getYears() + " tahun, " + period.getMonths() + " bulan, " + period.getDays() + " hari";
    }

    // Menghitung hari ulang tahun berikutnya
    public LocalDate hariUlangTahunBerikutnya(LocalDate lahir, LocalDate sekarang) {
        LocalDate ulangTahunBerikutnya = lahir.withYear(sekarang.getYear());
        if (!ulangTahunBerikutnya.isAfter(sekarang)) {
            ulangTahunBerikutnya = ulangTahunBerikutnya.plusYears(1);
        }
        return ulangTahunBerikutnya;
    }

    // Menerjemahkan teks hari ke bahasa Indonesia
    public String getDayOfWeekInIndonesian(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "Senin";
            case TUESDAY -> "Selasa";
            case WEDNESDAY -> "Rabu";
            case THURSDAY -> "Kamis";
            case FRIDAY -> "Jumat";
            case SATURDAY -> "Sabtu";
            case SUNDAY -> "Minggu";
            default -> "";
        };
    }

    // Memulai pengambilan data peristiwa secara asinkron
    public void fetchPeristiwaAsync(LocalDate ulangTahunBerikutnya, JTextArea txtAreaPeristiwa) {
        stopFetching = true;
        if (peristiwaThread != null && peristiwaThread.isAlive()) {
            peristiwaThread.interrupt();
        }
        stopFetching = false;

        peristiwaThread = new Thread(() -> {
            try {
                txtAreaPeristiwa.setText("Tunggu, sedang mengambil data...\n");
                getPeristiwaBarisPerBaris(ulangTahunBerikutnya, txtAreaPeristiwa, () -> stopFetching);

                if (!stopFetching) {
                    javax.swing.SwingUtilities.invokeLater(() -> 
                        txtAreaPeristiwa.append("Selesai mengambil data peristiwa\n"));
                }
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    javax.swing.SwingUtilities.invokeLater(() ->
                        txtAreaPeristiwa.setText("Pengambilan data dibatalkan.\n"));
                }
            }
        });
        peristiwaThread.start();
    }

    // Mendapatkan peristiwa penting secara baris per baris
    public void getPeristiwaBarisPerBaris(LocalDate tanggal, JTextArea txtAreaPeristiwa, Supplier<Boolean> shouldStop) {
        try {
            if (shouldStop.get()) {
                return;
            }

            String urlString = "https://byabbe.se/on-this-day/" +
                    tanggal.getMonthValue() + "/" + tanggal.getDayOfMonth() + "/events.json";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("HTTP response code: " + responseCode +
                        ". Silakan coba lagi nanti atau cek koneksi internet.");
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                if (shouldStop.get()) {
                    in.close();
                    conn.disconnect();
                    javax.swing.SwingUtilities.invokeLater(() ->
                        txtAreaPeristiwa.setText("Pengambilan data dibatalkan.\n"));
                    return;
                }
                content.append(inputLine);
            }
            in.close();
            conn.disconnect();

            JSONObject json = new JSONObject(content.toString());
            JSONArray events = json.getJSONArray("events");

            for (int i = 0; i < events.length(); i++) {
                if (shouldStop.get()) {
                    javax.swing.SwingUtilities.invokeLater(() ->
                        txtAreaPeristiwa.setText("Pengambilan data dibatalkan.\n"));
                    return;
                }

                JSONObject event = events.getJSONObject(i);
                String year = event.getString("year");
                String description = event.getString("description");
                String translatedDescription = translateToIndonesian(description);
                String peristiwa = year + ": " + translatedDescription;

                javax.swing.SwingUtilities.invokeLater(() ->
                    txtAreaPeristiwa.append(peristiwa + "\n"));
            }

            if (events.length() == 0) {
                javax.swing.SwingUtilities.invokeLater(() ->
                    txtAreaPeristiwa.setText("Tidak ada peristiwa penting yang ditemukan pada tanggal ini.\n"));
            }
        } catch (Exception e) {
            javax.swing.SwingUtilities.invokeLater(() ->
                txtAreaPeristiwa.setText("Gagal mendapatkan data peristiwa: " + e.getMessage()));
        }
    }

    // Menerjemahkan teks ke bahasa Indonesia
    private String translateToIndonesian(String text) {
        try {
            String urlString = "https://lingva.ml/api/v1/en/id/" +
                    text.replace(" ", "%20");
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("HTTP response code: " + responseCode);
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            conn.disconnect();

            JSONObject json = new JSONObject(content.toString());
            return json.getString("translation");
        } catch (Exception e) {
            return text + " (Gagal diterjemahkan)";
        }
    }
}