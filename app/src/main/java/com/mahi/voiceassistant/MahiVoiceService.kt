package com.mahi.voiceassistant

import android.app.*
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import java.util.*

class MahiVoiceService : Service(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private var isMahiAwake = false

    private var pendingNumber: String? = null
    private var pendingName: String? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        startNotification()
        initSpeechRecognizer()
    }

    private fun startNotification() {
        val channelId = "MahiChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Mahi Service", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Mahi Assistant Active")
            .setContentText("Listening for 'Mahi'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1, notification)
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        startListening()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val input = matches[0].lowercase(Locale.ROOT)
                    processCommand(input)
                }
                startListening()
            }

            override fun onError(error: Int) { startListening() }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    private fun processCommand(input: String) {
        if (pendingNumber != null) {
            if (input.contains("haan") || input.contains("yes") || input.contains("kar do")) {
                speakNatural("${pendingName} ko call kar rahi hoon")
                makeCall(pendingNumber!!)
                clearPending()
                return
            } else if (input.contains("nahi") || input.contains("no")) {
                speakNatural("Call cancel kar diya hai")
                clearPending()
                return
            }
        }

        if (!isMahiAwake) {
            if (input.contains("mahi") || input.contains("hey mahi")) {
                isMahiAwake = true
                speakNatural("Haan, main sun rahi hoon")
            }
            return
        }

        when {
            input.contains("call") || input.contains("phone") -> {
                val target = input.replace("mahi", "").replace("call", "").replace("ko", "").replace("kar", "").trim()
                handleCall(target)
            }
            input.contains("save") || input.contains("naya number") -> {
                handleSaveContact(input)
            }
            input.contains("emergency") || input.contains("help") -> {
                handleEmergencySOS()
            }
            input.contains("flashlight on") || input.contains("torch on") -> {
                toggleTorch(true)
                speakNatural("Flashlight on kar di hai")
            }
            input.contains("flashlight off") || input.contains("torch off") -> {
                toggleTorch(false)
                speakNatural("Flashlight off kar di hai")
            }
            else -> {
                speakNatural("Dobara boliye, samajh nahi aaya")
            }
        }
        isMahiAwake = false
    }

    private fun handleCall(targetName: String) {
        val contactsMap = getAllContacts()
        var exactMatch: Pair<String, String>? = null
        var fuzzyMatch: Pair<String, String>? = null

        for ((name, number) in contactsMap) {
            if (name.equals(targetName, ignoreCase = true)) {
                exactMatch = Pair(name, number)
                break
            } else if (name.lowercase(Locale.ROOT).contains(targetName.lowercase(Locale.ROOT))) {
                fuzzyMatch = Pair(name, number)
            }
        }

        if (exactMatch != null) {
            speakNatural("${exactMatch.first} ko call kar rahi hoon")
            makeCall(exactMatch.second)
        } else if (fuzzyMatch != null) {
            pendingName = fuzzyMatch.first
            pendingNumber = fuzzyMatch.second
            speakNatural("${targetName} naam se contact nahi mila, ${fuzzyMatch.first} mila hai. Kya isko call karun?")
        } else {
            speakNatural("${targetName} naam ka koi contact nahi mila")
        }
    }

    private fun handleSaveContact(input: String) {
        val digits = input.filter { it.isDigit() }
        val name = input.replace("mahi", "").replace("save", "").replace("number", "").replace("naya", "").replace(digits, "").trim()

        if (digits.length >= 10 && name.isNotEmpty()) {
            saveContactToPhone(name, digits)
            speakNatural("$name ka number save kar diya hai")
        } else {
            speakNatural("Naam aur number sahi se samajh nahi aaya")
        }
    }

    private fun saveContactToPhone(name: String, number: String) {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build())
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build())
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build())
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    private fun handleEmergencySOS() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val loc: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val message = if (loc != null) {
                "Emergency! Location: https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
            } else {
                "Emergency! Help me."
            }
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage("9876543210", null, message, null, null)
            speakNatural("Emergency message bhej diya gaya hai")
        } catch (e: Exception) {
            speakNatural("Location bhejne mein dikkat aayi")
        }
    }

    private fun toggleTorch(status: Boolean) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]
        cameraManager.setTorchMode(cameraId, status)
    }

    private fun getAllContacts(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val n = it.getString(nameIdx)
                val num = it.getString(numIdx)
                if (n != null && num != null) map[n] = num
            }
        }
        return map
    }

    private fun makeCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun speakNatural(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (v in tts.voices) {
                if (v.locale.language == "hi" && (v.name.contains("network") || v.name.contains("local"))) {
                    tts.voice = v
                    break
                }
            }
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun clearPending() {
        pendingName = null
        pendingNumber = null
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}