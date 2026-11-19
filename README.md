# Ptaki

Prywatna aplikacja na Androida: nasłuchuje otoczenia, pokazuje dźwięk na żywo (spektrogram)
i rozpoznaje ptaki po głosie. Każdy rozpoznany gatunek trafia na listę; po dotknięciu
pokazuje się zdjęcie i opis z Wikipedii oraz link do pełnego artykułu.

## Instalacja
APK buduje się automatycznie po każdym pushu (GitHub Actions).
Pobierz **Ptaki.apk** z zakładki *Releases → latest* i zainstaluj.

## Jak to działa
- Mikrofon 48 kHz → co 1,5 s analizowane są ostatnie 3 sekundy dźwięku.
- Rozpoznawanie offline modelem BirdNET V2.4 (TensorFlow Lite, ~6500 gatunków, polskie nazwy).
- Filtr gatunków wg lokalizacji i tygodnia roku (model „meta” BirdNET) — mniej pomyłek.
- Nasłuch działa też przy wygaszonym ekranie (usługa z powiadomieniem).
- Opisy: Wikipedia REST API (najpierw polska, awaryjnie angielska), zapisywane w telefonie.

## Licencje
- Model BirdNET (`app/src/main/assets/*.tflite`, `labels_pl.txt`): K. Lisa Yang Center for
  Conservation Bioacoustics (Cornell Lab of Ornithology) i Chemnitz University of Technology,
  **CC BY-NC-SA 4.0** — wyłącznie do użytku niekomercyjnego. https://birdnet.cornell.edu/
- Treści z Wikipedii: CC BY-SA.
