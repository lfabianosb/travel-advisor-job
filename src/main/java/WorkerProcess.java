import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import messenger.Slack;
import model.Cotacao;
import model.Flight;
import model.FlightMonitor;

public class WorkerProcess {
	private static final String SERVICE_ACCOUNT = "bigpromo-ds.json";
	private static final String BASE_TARGET = System.getenv("BASE_TARGET");
	private static final long SLEEP_TIME_BETWEEN_CICLES = Long.parseLong(System.getenv("SLEEP_TIME_BETWEEN_CICLES"));
	private static final int MSG_INFO_AFTER_N_TMES = Integer.parseInt(System.getenv("MSG_INFO_AFTER_N_TIMES"));
	private static final long SLEEP_TIME_BETWEEN_FLIGHTS = Long.parseLong(System.getenv("SLEEP_TIME_BETWEEN_FLIGHTS"));
	private static final String ZONE_ID = "GMT-03:00";
	private static final String DATABASE_URL = System.getenv("DATABASE_URL");
	private static final String DATABASE_NAME = System.getenv("DATABASE_NAME");
	private static final int TIMEOUT = 10000;

	private static FirebaseDatabase database;

	public static void main(String[] args) {
		WorkerProcess wp = new WorkerProcess();
		wp.run();
	}

	public static FirebaseDatabase getDatabaseFirebase() {
		if (database == null) {
			// Initialize Firebase
			try {
				FileInputStream serviceAccount = new FileInputStream(SERVICE_ACCOUNT);
				FirebaseOptions options = new FirebaseOptions.Builder()
						.setCredential(FirebaseCredentials.fromCertificate(serviceAccount)).setDatabaseUrl(DATABASE_URL)
						.build();
				FirebaseApp.initializeApp(options);
			} catch (IOException e) {
				System.out.println("ERROR: invalid service account credentials.");
				System.out.println(e.getMessage());
				System.exit(1);
			}

			database = FirebaseDatabase.getInstance();
		}

		return database;
	}

	private Map<String, FlightMonitor> getFlights() {
		final Map<String, FlightMonitor> flights = new HashMap<String, FlightMonitor>();

		DatabaseReference ref = WorkerProcess.getDatabaseFirebase().getReference(DATABASE_NAME);
		Query query = ref.orderByKey();
		final ValueEventListener listener = new ValueEventListener() {
			@Override
			public void onDataChange(DataSnapshot snapshot) {
				for (DataSnapshot data : snapshot.getChildren()) {
					flights.put(data.getKey(), data.getValue(FlightMonitor.class));
				}
			}

			@Override
			public void onCancelled(DatabaseError databaseError) {
				System.err.println("The read failed: " + databaseError.getCode());
			}
		};
		query.addValueEventListener(listener);

		try {
			Thread.sleep(TIMEOUT);
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(1);
		}
		query.removeEventListener(listener);

		return flights;
	}

	private void run() {
		int counter = 1;
		while (true) {
			Map<String, FlightMonitor> flights = getFlights();

			for (String key : flights.keySet()) {
				List<Cotacao> cotacoes = new ArrayList<Cotacao>();

				try {
					FlightMonitor fm = flights.get(key);
					System.out.println(fm);

					DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy");

					LocalDate start = LocalDate.parse(fm.getDtStart(), dtf);
					LocalDate end = LocalDate.parse(fm.getDtEnd(), dtf);
					int minDays = fm.getMinDays();
					int maxDays = fm.getMaxDays();

					if (start.plusDays(maxDays).isAfter(end)) {
						System.err.println("Intervalo maior que data final");
						continue;
					}

					int diff = maxDays - minDays;
					int period = (int) ChronoUnit.DAYS.between(start, end) - minDays;

					for (int i = 0; i <= period; i++) {
						for (int j = 0; j <= diff; j++) {
							LocalDate newStart = start.plusDays(i);
							LocalDate newEnd = newStart.plusDays(minDays + j);

							if (!newEnd.isAfter(end)) {

								DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

								String dtDep = newStart.format(formatter);
								String dtRet = newEnd.format(formatter);
								String nonStop = fm.isNonStop() ? "NS" : "-";
								String target = BASE_TARGET + "busca/voos-resultados#/" + fm.getFrom() + "/"
										+ fm.getTo() + "/RT/" + dtDep + "/" + dtRet + "/-/-/-/" + fm.getAdult() + "/"
										+ fm.getChild() + "/0/" + nonStop + "/-/-/-";

								System.out.println("target=" + target);

								String output = null;
								try {
									output = executeCommandWithProcessBuilder("casperjs", "viajanet.js", target);
								} catch (Exception e) {
									String msg = "[" + getCurrentDateTime()
											+ "] Erro ao executar o script viajanet.js.\nTarget=" + target
											+ "\nMessage=" + e.getMessage();
									new Slack().sendMessage(msg, Slack.ERROR);
									System.err.println(msg);
									continue;
								}

								System.out.println("output=" + output);

								Flight flight = null;
								try {
									flight = new Gson().fromJson(output, Flight.class);
								} catch (Exception e) {
									String msg = "[" + getCurrentDateTime()
											+ "] Erro ao converter de json para objeto.\nString=" + output + "\nTarget="
											+ target + "\nMessage=" + e.getMessage();
									new Slack().sendMessage(msg, Slack.ERROR);
									System.err.println(msg);
									continue;
								}

								System.out.println(flight);

								if (flight.getMsg() != null && flight.getMsg().length() > 0) {
									String msg = "[" + getCurrentDateTime() + "] Mensagem de erro vinda do script: "
											+ flight.getMsg();
									new Slack().sendMessage(msg, Slack.ERROR);
									System.err.println(msg);
								} else {
									// Pesquisa de valor realizada com sucesso
									cotacoes.add(new Cotacao(flight.getCia(), newStart.format(dtf), newEnd.format(dtf),
											flight.getValor(), getCurrentDateTime()));

									// Verificar se o preço foi atingido
									if (fm.getAlertPrice() > flight.getValor()) {
										String now = getCurrentDateTime();
										String msg = "[" + now + "] Comprar voo de " + fm.getFrom() + " para "
												+ fm.getTo() + " da " + flight.getCia() + " por *" + flight.getValor()
												+ "* no período de " + newStart.format(dtf) + " a " + newEnd.format(dtf)
												+ " para " + fm.getAdult() + " adulto(s) e " + fm.getChild()
												+ " criança(s)";

										System.out.println("[" + now + "] " + msg);
										new Slack().sendMessage(msg, Slack.ALERT);
									}
								}

								try {
									Thread.sleep(SLEEP_TIME_BETWEEN_FLIGHTS * 1000);
								} catch (InterruptedException e) {
									String now = getCurrentDateTime();
									new Slack().sendMessage("[" + now + "] Erro: " + e.getMessage(), Slack.ERROR);
									System.err.println("[" + now + "] Erro: " + e.getMessage());
								}

							}
						}
					}

					if (cotacoes.size() > 0) {
						// Existem cotacoes para adicionar ao Firebase

						System.out.println("Adicionando cotações ao Firebase...");

						DatabaseReference ref = WorkerProcess.getDatabaseFirebase()
								.getReference(DATABASE_NAME + "/" + key + "/cotacoes");
						ref.setValue(cotacoes);
					}

				} catch (Exception e) {
					String now = getCurrentDateTime();
					new Slack().sendMessage("[" + now + "] Erro: " + e.getMessage(), Slack.ERROR);
					System.err.println("[" + now + "] Erro: " + e.getMessage());
				}
			}

			String now = getCurrentDateTime();
			System.out.println("[" + now + "] Ciclo " + counter);

			if ((counter % MSG_INFO_AFTER_N_TMES) == 0) {
				new Slack().sendMessage("[" + now + "] Travel Advisor is working!", Slack.INFO);
			}

			// Reset counter
			if (counter++ > 10000)
				counter = 1;

			// Aguardar um pouco antes de reiniciar o ciclo de pesquisas
			try {
				Thread.sleep(SLEEP_TIME_BETWEEN_CICLES * 1000);
			} catch (InterruptedException e) {
				now = getCurrentDateTime();
				new Slack().sendMessage("[" + now + "] Erro: " + e.getMessage(), Slack.ERROR);
				System.err.println("[" + now + "] Erro: " + e.getMessage());
			}
		}
	}

	/**
	 * Executar um comando na console
	 * 
	 * @return saída da console
	 */
	private String executeCommandWithProcessBuilder(String command, String script, String param) {
		StringBuffer output = new StringBuffer();

		ProcessBuilder pb;
		try {
			pb = new ProcessBuilder(command, script, param);
			Process p = pb.start();
			p.waitFor();

			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = "";
			while ((line = reader.readLine()) != null) {
				output.append(line);
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return output.toString();
	}

	/**
	 * Obter data e hora corrente
	 * 
	 * @return Data e hora corrente no formato dd/MM/yyyy HH:mm:ss
	 */
	private String getCurrentDateTime() {
		ZoneId zoneId = ZoneId.of(ZONE_ID);
		LocalDateTime now = LocalDateTime.now(zoneId);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
		return now.format(formatter);
	}

}
