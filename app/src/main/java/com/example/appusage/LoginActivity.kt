package com.example.appusage

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class LoginActivity : ComponentActivity(){
    private lateinit var auth: FirebaseAuth

    private val signInLauncher = registerForActivityResult(FirebaseAuthUIActivityResultContract()) { result ->
        onSignInResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        auth= Firebase.auth
        val currentUser=auth.currentUser

        if(currentUser==null){
            signInAnonymously()
        }else{
            navigateToMainActivity()
        }
    }

    private fun signInAnonymously() {
        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "signInAnonymously:success")
                    navigateToMainActivity()
                } else {
                    Log.w("LoginActivity", "signInAnonymously:failure", task.exception)
                    Toast.makeText(this, "Authentication failed. Please retry.", Toast.LENGTH_SHORT).show()
                }
            }
    }


    private fun startFirebaseUILogin() {
        val providers = arrayListOf(
            AuthUI.IdpConfig.AnonymousBuilder().build()
        )

        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .build()

        signInLauncher.launch(signInIntent)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            navigateToMainActivity()
        } else {
            Toast.makeText(this, "Sign-in canceled or failed. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

}