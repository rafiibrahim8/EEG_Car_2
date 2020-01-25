package ml.nerdsofku.eegcarx2;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class PhoneNumberActivity extends AppCompatActivity {

    @BindView(R.id.etPhoneNo)
    EditText etPhoneNo;
    @BindView(R.id.btnPhoneNo)
    Button btnPhoneNo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_number);
        ButterKnife.bind(this);
    }

    @OnClick(R.id.btnPhoneNo)
    void onButtonClick(){
        String phone = etPhoneNo.getText().toString();
        if(Pattern.compile("^(\\+88)?01[13456789][0-9]{8}\\b").matcher(phone).matches()){
            Intent intent = new Intent(PhoneNumberActivity.this,MainActivity.class);
            intent.putExtra("eeg_2_ph_number",phone);
            startActivity(intent);
            finish();
        }
        else{
            Toast.makeText(this,"Enter a valid BD phone number.",Toast.LENGTH_LONG).show();
        }
    }
}
