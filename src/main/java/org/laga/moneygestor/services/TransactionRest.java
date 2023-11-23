package org.laga.moneygestor.services;

import org.laga.moneygestor.db.entity.TransactionDb;
import org.laga.moneygestor.db.entity.WalletDb;
import org.laga.moneygestor.db.repository.TransactionRepository;
import org.laga.moneygestor.db.repository.UserRepository;
import org.laga.moneygestor.db.repository.WalletRepository;
import org.laga.moneygestor.logic.TransactionGestor;
import org.laga.moneygestor.logic.UserGestor;
import org.laga.moneygestor.services.exceptions.MoneyGestorErrorSample;
import org.laga.moneygestor.services.json.CreateWallet;
import org.laga.moneygestor.services.json.Transaction;
import org.laga.moneygestor.services.json.TransactionForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/transaction")
public class TransactionRest {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Autowired
    public TransactionRest(UserRepository userRepository, WalletRepository walletRepository, TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping("/new")
    public void addNewTransaction(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody TransactionForm transactionForm) {
        var user = userRepository.findFromToken(authorization);
        if(user == null)
            throw MoneyGestorErrorSample.USER_NOT_FOUND;

        UserGestor userGestor = UserGestor.Builder.createFromDB(user);

        if(!userGestor.tokenIsValid())
            throw MoneyGestorErrorSample.USER_TOKEN_NOT_VALID;

        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");


        TransactionDb transactionDb = new TransactionDb();
        transactionDb.setDescription(transactionForm.getDescription());
        transactionDb.setDate(LocalDate.parse(transactionForm.getDate(), formatter));
        transactionDb.setValue(transactionForm.getValue());
        transactionDb.setWalletId(transactionForm.getWallet());
        transactionDb.setUserId(userGestor.getId());

        transactionRepository.save(transactionDb);
    }

    @GetMapping("/list")
    public List<Transaction> getTransaction(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestParam(name = "sort", required = false) String sortParams) {
        var user = userRepository.findFromToken(authorization);
        if(user == null)
            throw MoneyGestorErrorSample.USER_NOT_FOUND;

        UserGestor userGestor = UserGestor.Builder.createFromDB(user);

        if(!userGestor.tokenIsValid())
            throw MoneyGestorErrorSample.USER_TOKEN_NOT_VALID;

        TransactionDb transactionExample = new TransactionDb();
        transactionExample.setUserId(userGestor.getId());

        return TransactionGestor.convertToRest(transactionRepository.findAll(Example.of(transactionExample)));
    }
}
