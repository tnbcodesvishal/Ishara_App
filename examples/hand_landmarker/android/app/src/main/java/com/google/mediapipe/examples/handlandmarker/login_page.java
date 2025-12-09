package com.google.mediapipe.examples.handlandmarker;



import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
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
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import org.w3c.dom.Text;

import java.util.regex.Pattern;

public class login_page extends AppCompatActivity {

    private TextView create_account;
    private EditText loginemail,loginpassword;
    private LinearLayout loginbtn,withgoogle;
    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;
    AlertDialog loadingDialog;


    private final ActivityResultLauncher<Intent> activityResultLauncher=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        @Override
        public void onActivityResult(ActivityResult result) {
            if(result.getResultCode()==RESULT_OK){
                Task<GoogleSignInAccount> accountTask= GoogleSignIn.getSignedInAccountFromIntent(result.getData());

                try{
                    GoogleSignInAccount signInAccount=accountTask.getResult(ApiException.class);
                    AuthCredential authCredential= GoogleAuthProvider.getCredential(signInAccount.getIdToken(),null);
                    auth.signInWithCredential(authCredential).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            if (task.isSuccessful()){
                                auth=FirebaseAuth.getInstance();
                                Toast.makeText(login_page.this, "Sign in successfully", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(login_page.this,MainMain.class));
                                finish();

                            }
                            else {
                                Toast.makeText(login_page.this, "Failed to Sign in:" + task.getException(), Toast.LENGTH_SHORT).show();
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
        setContentView(R.layout.activity_login_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        FirebaseApp.initializeApp(this);
        auth=FirebaseAuth.getInstance();
        create_account=findViewById(R.id.createaccount);
        loginbtn=findViewById(R.id.loginbtn);
        loginemail=findViewById(R.id.loginemail);
        loginpassword=findViewById(R.id.loginpassword);
        withgoogle=findViewById(R.id.logingoogle);



        create_account.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(login_page.this,sign_up.class));
            }
        });

//        if (auth.getCurrentUser() != null) {
//            // User is already logged in, redirect to MainActivity
//            Intent intent = new Intent(login_page.this, home_page.class);
//            startActivity(intent);
//            finish(); // Close LoginActivity so user can't go back
//        }

        loginbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String email = loginemail.getText().toString().trim();
                String password = loginpassword.getText().toString().trim();


                if (!email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

                    if (!password.isEmpty()) {
                        showLoadingDialog();
                        auth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                                    @Override
                                    public void onSuccess(AuthResult authResult) {
                                        Toast.makeText(login_page.this, "Login successful", Toast.LENGTH_SHORT).show();
                                        hideLoadingDialog();
                                            startActivity(new Intent(login_page.this, MainMain.class));
                                        finish();
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(login_page.this, "Login failed", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        loginpassword.setError("password cannot be empty");
                    }
                }
                else if (email.isEmpty()) {
                    loginemail.setError("email cannot be empty");
                } else {
                    loginemail.setError("please enter valid email");
                }
            }


        });

        GoogleSignInOptions options=new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient=GoogleSignIn.getClient(login_page.this,options);

        withgoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent=googleSignInClient.getSignInIntent();
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