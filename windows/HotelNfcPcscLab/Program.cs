using System.Diagnostics;
using System.Text.Json;
using PCSC;
using PCSC.Iso7816;

const string Aid = "F0484F54454C01";
var entries = new List<object>();
var started = DateTimeOffset.Now;
Console.WriteLine("Hotel NFC Wallet Lab | PC/SC | ACR122U");
Console.WriteLine("Uso restrito a testes. Não autentica MIFARE Classic e não libera BIS.\n");
var mode = Ask("Teste: [A] MIFARE físico [B] Android HCE [C] Google Wallet", "B");
var results = new Dictionary<string, object?> {
    ["test_type"] = mode, ["started_at"] = started, ["aid"] = Aid,
    ["reader_connected"] = false, ["card_detected"] = false,
    ["aid_selected"] = false, ["apdu_responded"] = false,
    ["observed_protocol"] = "Não testado", ["smart_tap"] = "Não testado",
    ["bis_compatible"] = "Não demonstrado"
};

void Log(string evt, object? details = null) {
    var record = new { time = DateTimeOffset.Now, evt, details };
    entries.Add(record);
    Console.WriteLine(record.time.ToString("O") + " | " + evt + " | " + details);
}

try {
    using var context = ContextFactory.Instance.Establish(SCardScope.System);
    var names = context.GetReaders();
    if (names == null || names.Length == 0) {
        Log("Nenhum leitor PC/SC detectado");
    } else {
        for (int i = 0; i < names.Length; i++) Console.WriteLine("[" + i + "] " + names[i]);
        var choice = Ask("Índice do leitor", "0");
        if (!int.TryParse(choice, out int index) || index < 0 || index >= names.Length) throw new Exception("Índice inválido");
        results["reader_name"] = names[index];
        results["reader_connected"] = true;
        Console.WriteLine("Aproxime o cartão/celular do leitor e pressione ENTER...");
        Console.ReadLine();
        try {
            using var card = context.ConnectReader(names[index], SCardShareMode.Shared, SCardProtocol.Any);
            results["card_detected"] = true;
            var status = card.GetStatus();
            var atr = status.GetAtr() ?? Array.Empty<byte>();
            results["atr_hex"] = Convert.ToHexString(atr);
            results["pcsc_protocol"] = status.Protocol.ToString();
            results["observed_protocol"] = "PC/SC " + status.Protocol + "; tecnologia RF não confirmada pelo ATR";
            Log("Cartão/dispositivo ativado", new { atr = Convert.ToHexString(atr), pcsc = status.Protocol.ToString() });
            Log("Nota técnica", "PCSC T=1 e ATR sintético não provam ISO-DEP nem MIFARE Classic");

            // This test AID belongs to our Android app only, not to Wallet or the BIS system.
            using var isoReader = new IsoReader(context, names[index], SCardShareMode.Shared, SCardProtocol.Any, false);
            var select = new CommandApdu(IsoCase.Case3Short, isoReader.ActiveProtocol) {
                CLA = 0x00, Instruction = InstructionCode.SelectFile, P1 = 0x04, P2 = 0x00,
                Data = Convert.FromHexString(Aid)
            };
            var sw = Transmit(isoReader, select, "SELECT AID " + Aid);
            if (sw == "9000") {
                results["aid_selected"] = true;
                results["observed_protocol"] = "AID experimental selecionado por APDU (HCE/ISO-DEP provável; confirmar captura RF)";
                if (mode.Equals("B", StringComparison.OrdinalIgnoreCase)) {
                    var getData = new CommandApdu(IsoCase.Case2Short, isoReader.ActiveProtocol) {
                        CLA = 0x80, Instruction = (InstructionCode)0xCA, P1 = 0x00, P2 = 0x00, Le = 0x00
                    };
                    var resultSw = Transmit(isoReader, getData, "GET DATA 80CA000000");
                    results["apdu_responded"] = resultSw == "9000";
                }
            }
        } catch (Exception ex) {
            Log("Sem resposta/erro de conexão PCSC", ex.GetType().Name + ": " + ex.Message);
        }
    }
} catch (Exception ex) {
    Log("Erro de contexto/leitor", ex.GetType().Name + ": " + ex.Message);
}
results["finished_at"] = DateTimeOffset.Now;
results["events"] = entries;
var output = Path.Combine(Environment.CurrentDirectory, "nfc-report-" + DateTime.Now.ToString("yyyyMMdd-HHmmss"));
File.WriteAllText(output + ".json", JsonSerializer.Serialize(results, new JsonSerializerOptions { WriteIndented = true }));
File.WriteAllLines(output + ".txt", new[] {
    "Hotel NFC Wallet Lab - relatório experimental",
    "Experimento " + mode,
    "Leitor: " + results.GetValueOrDefault("reader_name"),
    "ATR: " + results.GetValueOrDefault("atr_hex"),
    "Protocolo observado: " + results["observed_protocol"],
    "AID selecionado: " + results["aid_selected"],
    "APDU respondida: " + results["apdu_responded"],
    "BIS compatível: NÃO DEMONSTRADO",
    "Não houve comando de autenticação MIFARE, leitura de setor ou alteração de fechadura."
}.Concat(entries.Select(x => JsonSerializer.Serialize(x))));
Console.WriteLine("\nRelatórios: " + output + ".json/.txt");

static string Ask(string prompt, string fallback) {
    Console.Write(prompt + " [" + fallback + "]: ");
    var value = Console.ReadLine();
    return string.IsNullOrWhiteSpace(value) ? fallback : value.Trim();
}

string Transmit(IsoReader reader, CommandApdu apdu, string label) {
    var data = apdu.ToArray();
    var timer = Stopwatch.StartNew();
    Log("TX " + label, Convert.ToHexString(data));
    try {
        var response = reader.Transmit(apdu);
        timer.Stop();
        var body = response.GetData() ?? Array.Empty<byte>();
        var sw = response.SW1.ToString("X2") + response.SW2.ToString("X2");
        Log("RX " + label, new { response_hex = Convert.ToHexString(body) + sw,
                                 sw, elapsed_ms = timer.Elapsed.TotalMilliseconds });
        return sw;
    } catch (Exception ex) {
        timer.Stop();
        Log("Erro APDU " + label, new { error = ex.Message, elapsed_ms = timer.Elapsed.TotalMilliseconds });
        return "TRANSPORT_ERROR";
    }
}
