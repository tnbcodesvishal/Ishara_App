package com.google.mediapipe.examples.handlandmarker;



import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Firebase;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class sign_up extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText username, email, password, confirm_password;
    private LinearLayout create_acount, withgoogle;
    private TextView loginpage;
    private FirebaseFirestore fstore;
    private String UserId;
    AlertDialog loadingDialog;


    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        @Override
        public void onActivityResult(ActivityResult result) {
            if (result.getResultCode() == RESULT_OK) {
                Task<GoogleSignInAccount> accountTask = GoogleSignIn.getSignedInAccountFromIntent(result.getData());

                try {
                    GoogleSignInAccount signInAccount = accountTask.getResult(ApiException.class);
                    AuthCredential authCredential = GoogleAuthProvider.getCredential(signInAccount.getIdToken(), null);
                    auth.signInWithCredential(authCredential).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {
                                auth = FirebaseAuth.getInstance();
                                Toast.makeText(sign_up.this, "Sign in successfully", Toast.LENGTH_SHORT).show();


                                startActivity(new Intent(sign_up.this, MainMain.class));
                                finish();

                            } else {
                                Toast.makeText(sign_up.this, "Failed to Sign in:" + task.getException(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (ApiException e) {
                    e.printStackTrace();
                }
            }
        }
    });


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        fstore = FirebaseFirestore.getInstance();
        username = findViewById(R.id.username);
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        confirm_password = findViewById(R.id.confirmpassword);
        create_acount = findViewById(R.id.createaco);
        loginpage = findViewById(R.id.loginpage);
        withgoogle = findViewById(R.id.with_google);



        if (!InternetUtil.isConnected(this)) {
            InternetUtil.showNoInternetDialog(this);
        }

        loginpage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(sign_up.this, login_page.class));

            }
        });


        if (auth.getCurrentUser() != null) {
            // User is already logged in, redirect to MainActivity
            Intent intent = new Intent(sign_up.this, MainMain.class);
            startActivity(intent);
            finish(); // Close LoginActivity so user can't go back
        }

        create_acount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String user_name = username.getText().toString().trim();
                String user_email = email.getText().toString().trim();
                String user_pass = password.getText().toString().trim();
                String user_con_pass = confirm_password.getText().toString().trim();


                if (user_name.isEmpty()) {
                    username.setError("username connot be empty");
                }
                else if (user_email.isEmpty()) {
                    email.setError("Email connot be empty");
                }
                else if (user_pass.isEmpty()) {
                    password.setError("password connot be empty");
                }
                else if (user_con_pass.isEmpty()) {
                    Toast.makeText(sign_up.this, "plz enter confirm password", Toast.LENGTH_SHORT).show();
                }

                else if (user_pass.equals(user_con_pass)) {
                    showLoadingDialog();
                    auth.createUserWithEmailAndPassword(user_email, user_pass).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()) {


                                UserId = auth.getCurrentUser().getUid();
                                Toast.makeText(sign_up.this, "Account created Successfully", Toast.LENGTH_SHORT).show();

                                DocumentReference documentReference = fstore.collection("users").document(UserId);
                                Map<String, Object> user = new HashMap<>();
                                user.put("username", user_name);
                                user.put("email", user_email);
                                user.put("password", user_pass);
                                documentReference.set(user).addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {

                                        hideLoadingDialog();
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {

                                    }
                                });

                                startActivity(new Intent(sign_up.this, MainMain.class));
                                finish();
                            } else {
                                Toast.makeText(sign_up.this, "Failed" + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                hideLoadingDialog();
                            }
                        }
                    });
                } else {
                    Toast.makeText(sign_up.this, "password is not same", Toast.LENGTH_SHORT).show();
                }


            }
        });


        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(sign_up.this, options);

        withgoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = googleSignInClient.getSignInIntent();
                activityResultLauncher.launch(intent);
            }
        });


    }

    private void showLoadingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_loading, null);
        builder.setView(view);
        builder.setCancelable(false); // Prevent dismiss on back press
        loadingDialog = builder.create();
        loadingDialog.show();
    }

    private void hideLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    @Override
    public void onBackPressed() {
        // If drawer is open, close it first

        // Kill the app
        super.onBackPressed();
        finishAffinity();   // Close all activities
        System.exit(0);     // Kill the process

    }
}