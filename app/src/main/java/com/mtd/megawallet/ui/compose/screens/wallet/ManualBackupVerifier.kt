package com.mtd.megawallet.ui.compose.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.megawallet.ui.compose.components.TopHeader
import com.mtd.megawallet.ui.compose.screens.addexistingwallet.manualimport.InputSeedPhraseSection
import com.mtd.megawallet.ui.compose.screens.addexistingwallet.manualimport.ValidationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ManualBackupVerifier(
    mnemonic: String,
    onBackupConfirmed: () -> Unit,
) {
    val words = mnemonic.split(" ").filter { it.isNotEmpty() }
    val scope = rememberCoroutineScope()
    
    // Game Logic State
    var questionIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) } // جلوگیری از کلیک‌های تکراری
    
    // User Answers map
    val userAnswers = remember { mutableStateMapOf<Int, Int>() }
    
    // Validation states
    val validationStates = remember { mutableStateMapOf<Int, ValidationState>() }

    // Initialize game
    LaunchedEffect(Unit) {
        if (questionIndices.isEmpty()) {
            questionIndices = (0 until 12).shuffled().take(4)
        }
    }
    
    // این متد برای ایجاد سوال جدید در صورت پاسخ اشتباه است
    // ما باید یک ایندکس جدید که جزو سوالات قبلی موفق نبوده و همین سوال فعلی هم نباشد انتخاب کنیم
    fun getNewRandomQuestion(currentIdx: Int, usedIndices: List<Int>): Int {
       val availableIndices = (0 until 12).filter { it != currentIdx && !usedIndices.contains(it) }
       // اگر احیاناً ایندکسی نمانده بود (که بعید است)، همان را برمی‌گرداند یا تصادفی
       return if (availableIndices.isNotEmpty()) availableIndices.random() else currentIdx
    }

    Box(modifier = Modifier.fillMaxSize()) {



         VerifyPhrasesContent(
            words = words,
            questionIndices = questionIndices,
            currentQuestionIndex = currentQuestionIndex,
            userAnswers = userAnswers,
            validationStates = validationStates,
            onAnswerSelected = { selectedPosition ->
                if (isProcessing) return@VerifyPhrasesContent
                isProcessing = true
                
                val currentRealIndex = questionIndices[currentQuestionIndex]
                val isCorrect = selectedPosition == (currentRealIndex + 1)
                
                scope.launch {
                    if (isCorrect) {
                        validationStates[currentRealIndex] = ValidationState.Success
                        userAnswers[currentQuestionIndex] = selectedPosition
                        
                        delay(500) // مکث برای دیدن رنگ سبز
                        
                        if (currentQuestionIndex < 2) {
                             currentQuestionIndex++
                        } else {
                            onBackupConfirmed()
                        }
                    } else {
                        validationStates[currentRealIndex] = ValidationState.Error
                        delay(800) // مکث برای دیدن رنگ قرمز
                        
                        // تعویض سوال فعلی با یک سوال جدید
                        val completedIndices = questionIndices.take(currentQuestionIndex)
                        val newIndex = getNewRandomQuestion(currentRealIndex, completedIndices)
                        
                        val newQuestions = questionIndices.toMutableList()
                        newQuestions[currentQuestionIndex] = newIndex
                        questionIndices = newQuestions
                        
                        validationStates.remove(currentRealIndex) 
                        validationStates[newIndex] = ValidationState.None
                    }
                    isProcessing = false
                }
            }
        )
    }
}


@Composable
private fun VerifyPhrasesContent(
    words: List<String>,
    questionIndices: List<Int>,
    currentQuestionIndex: Int,
    userAnswers: Map<Int, Int>,
    validationStates: Map<Int, ValidationState>,
    onAnswerSelected: (Int) -> Unit
) {
    if (questionIndices.isEmpty()) return

    val targetIndex = questionIndices[currentQuestionIndex]
    val targetWord = words[targetIndex]
    
    // لیستی برای InputSeedPhraseSection که فقط کلمه هدف در آن پر شده است
    val userInputs = remember(targetIndex, targetWord) {
        List(12) { i -> if (i == targetIndex) targetWord else "" }
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        TopHeader(
            "تأیید نسخه پشتیبان",
            "برای اطمینان از اینکه همه موارد را به درستی ذخیره کرده اید، این تست کوتاه را انجام دهید",
            Modifier
                .fillMaxWidth()
                .padding( start = 20.dp, end = 20.dp)
        )


        InputSeedPhraseSection(
            currentIndex = targetIndex,
            userInputs = userInputs,
            wordCount = 12,
            onTextChange = { _, _ -> }, // ReadOnly
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            isTestMode = true,
            validationStateMap = validationStates,
            hideIndex = true
        )

        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { index ->
                val isCompleted = index < currentQuestionIndex
                val isCurrent = index == currentQuestionIndex

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(if (isCurrent) 24.dp else 8.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isCompleted || isCurrent) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.surface
                        )
                )
            }
        }
        
        Text(
            text = "شماره این کلمه را انتخاب کنید",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
            modifier = Modifier.padding(top = 18.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                // سطر اول: 1, 2, 3
                Row(modifier = Modifier.weight(1f)) {
                    NumberKey(1, Modifier.weight(1f), onAnswerSelected)
                    NumberKey(2, Modifier.weight(1f), onAnswerSelected)
                    NumberKey(3, Modifier.weight(1f), onAnswerSelected)
                }
                // سطر دوم: 4, 5, 6
                Row(modifier = Modifier.weight(1f)) {
                    NumberKey(4, Modifier.weight(1f), onAnswerSelected)
                    NumberKey(5, Modifier.weight(1f), onAnswerSelected)
                    NumberKey(6, Modifier.weight(1f), onAnswerSelected)
                }
                // سطر سوم: 7, 8, 9
                Row(modifier = Modifier.weight(1f)) {
                    NumberKey(7, Modifier.weight(1f), onAnswerSelected)
                    NumberKey(8, Modifier.weight(1f), onAnswerSelected)
                    NumberKey(9, Modifier.weight(1f), onAnswerSelected)
                }
                // سطر چهارم: 10, 11, 12
                Row(modifier = Modifier.weight(1f)) {
                    NumberKey(10, Modifier.weight(1f), onAnswerSelected)
                    NumberKey(11, Modifier.weight(1f), onAnswerSelected)
                    NumberKey(12, Modifier.weight(1f), onAnswerSelected)
                }
            }
        }
    }
}

@Composable
private fun NumberKey(
    number: Int,
    modifier: Modifier = Modifier,
    onClick: (Int) -> Unit
) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .height(56.dp) // ارتفاع ثابت برای دکمه‌ها
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick(number) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            color = MaterialTheme.colorScheme.onTertiary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
