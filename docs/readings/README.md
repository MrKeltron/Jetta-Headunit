# Manufacturer reading lists

Generic OBD2 leaves out a lot (oil temperature and engine torque on many cars). Drehzahl can also ask the engine
computer for values by the maker's own ID numbers (UDS service 22). Those numbers differ per maker and engine, so
Drehzahl ships with an empty list and you add the ones for your car.

## Using a list from this folder

1. Find the file for your engine below. If there is none, see "Finding your own".
2. Copy its contents into `Android/data/dev.kelton.drehzahl/files/readings.txt` on the tablet (any file manager, or
   `adb push`). Drehzahl creates that file, with the format explained inside, the first time the OBD2 adapter connects.
3. Reconnect the adapter (Car > Data source > Reconnect).

| File | Engine | Checked on |
|---|---|---|
| `vw-ea888-gen3.txt` | VW/Audi 2.0 TSI (EA888 gen 3, Simos 18 ECU) | 2015 Jetta GLI |

## Finding your own

Car > Trouble codes > OBD console > Car scan asks the engine computer for every ID in a range and saves the ones it
answers to `files/scans`. It only reads. A quick scan takes around 40 minutes with the engine running, so do it outside.
List candidate IDs in `readings.txt` under any name; Drehzahl then logs them every 2 s next to the generic values in
`files/readings/*.csv`, which is how you match an ID to a gauge on the dash.

A list that works is welcome as a pull request.

## Format

```
name  request-header  ID  formula
oil   7E0             202F  k10
```

Names `oil`, `torque` and `torque_req` feed the gauges and the pull recorder. Any other name is only logged.
Formulas: `u8`, `u8-40`, `u16`, `s16`, `u16/10`, `s16/10`, `k10` (tenths of a kelvin to degrees C), `u32`, `raw`.
A line `engine 1.984` sets the engine size in litres unless it is set in Car > Data source.
