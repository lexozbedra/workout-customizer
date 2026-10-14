package app.sports.workout

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import app.sports.Store
import app.sports.core.Kind

/**
 * Два сигнала, которые рисуются прямо в коде (файлов со звуком нет):
 * работа – два коротких высоких писка, отдых – один длинный низкий. В конце тренировки – два низких.
 *
 * Музыка не останавливается: сигнал идёт отдельным потоком воспроизведения. Если включено приглушение, на время сигнала
 * запрашивается кратковременный фокус «можно приглушить» (Spotify, YouTube и плееры делают тише и потом возвращают громкость).
 */
class Signals(private val context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val rate = 22050

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val work = pcm(listOf(Triple(1500.0, 0.0, 0.14), Triple(1500.0, 0.24, 0.14)))
    private val rest = pcm(listOf(Triple(620.0, 0.0, 0.55)))
    private val done = pcm(listOf(Triple(620.0, 0.0, 0.5), Triple(620.0, 0.7, 0.5)))

    fun play(kind: Kind) = play(if (kind == Kind.WORK) work else rest)

    fun playFinish() = play(done)

    /** Тоны: частота, начало (с), длительность (с). Перед сигналом небольшая тишина, чтобы музыка успела стихнуть. */
    private fun pcm(tones: List<Triple<Double, Double, Double>>): ShortArray {
        val lead = 0.12
        val tail = 0.2
        val total = lead + tones.maxOf { it.second + it.third } + tail
        val n = (total * rate).toInt()
        val out = ShortArray(n)
        val att = (0.008 * rate).toInt()
        for ((freq, start, dur) in tones) {
            val s0 = ((lead + start) * rate).toInt()
            val len = (dur * rate).toInt()
            for (i in 0 until len) {
                val env = minOf(1.0, i.toDouble() / att, (len - i).toDouble() / att)
                val v = Math.sin(2 * Math.PI * freq * i / rate) * env * 0.85
                val idx = s0 + i
                if (idx < n) out[idx] = (v * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    private fun play(samples: ShortArray) {
        var request: AudioFocusRequest? = null
        try {
            if (Store.get(context).current.db.settings.duck) {
                request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audio.requestAudioFocus(request)
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track.play()
            val finishIn = samples.size * 1000L / rate + 150L
            val req = request
            handler.postDelayed({
                try { track.stop() } catch (_: Exception) { }
                track.release()
                if (req != null) audio.abandonAudioFocusRequest(req)
            }, finishIn)
        } catch (_: Exception) {
            request?.let { audio.abandonAudioFocusRequest(it) }
        }
    }

    // Отложенные освобождения треков не отменяем: иначе звук и фокус остались бы занятыми.
}
