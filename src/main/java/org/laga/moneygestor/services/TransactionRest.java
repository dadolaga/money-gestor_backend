package org.laga.moneygestor.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.laga.moneygestor.db.entity.TransactionDb;
import org.laga.moneygestor.db.entity.TransactionGraphView;
import org.laga.moneygestor.db.entity.TransactionTableView;
import org.laga.moneygestor.db.entity.WalletDb;
import org.laga.moneygestor.db.repository.*;
import org.laga.moneygestor.logic.SortGestor;
import org.laga.moneygestor.logic.TransactionGestor;
import org.laga.moneygestor.logic.UserGestor;
import org.laga.moneygestor.services.exceptions.MoneyGestorErrorSample;
import org.laga.moneygestor.services.json.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.provider.HibernateUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/transaction")
public class TransactionRest {

    private static final int ID_EXCHANGE_TYPE = 1;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTableRepository transactionTableRepository;
    private final TransactionGraphRepository transactionGraphRepository;

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    public TransactionRest(UserRepository userRepository, WalletRepository walletRepository, TransactionRepository transactionRepository, TransactionTableRepository transactionTableRepository, TransactionGraphRepository transactionGraphRepository) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.transactionTableRepository = transactionTableRepository;
        this.transactionGraphRepository = transactionGraphRepository;
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
        transactionDb.setValue(transactionForm.getTypeId() == ID_EXCHANGE_TYPE? transactionForm.getValue().negate() : transactionForm.getValue());
        transactionDb.setWalletId(transactionForm.getWallet());
        transactionDb.setUserId(userGestor.getId());
        transactionDb.setTypeId(transactionForm.getTypeId());

        TransactionDb transactionSaved = transactionRepository.save(transactionDb);

        if(transactionForm.getTypeId() != ID_EXCHANGE_TYPE)
            return;

        TransactionDb transactionDestinationDb = new TransactionDb();
        transactionDestinationDb.setDescription(transactionForm.getDescription());
        transactionDestinationDb.setDate(LocalDate.parse(transactionForm.getDate(), formatter));
        transactionDestinationDb.setValue(transactionForm.getValue());
        transactionDestinationDb.setWalletId(transactionForm.getWalletDestination());
        transactionDestinationDb.setTransactionDestinationId(transactionSaved.getId());
        transactionDestinationDb.setUserId(userGestor.getId());
        transactionDestinationDb.setTypeId(transactionForm.getTypeId());

        TransactionDb transactionDestinationSaved = transactionRepository.save(transactionDestinationDb);

        transactionSaved.setTransactionDestinationId(transactionDestinationSaved.getId());

        transactionRepository.save(transactionSaved);
    }

    @GetMapping("/list")
    public List<TransactionTableView> getTransaction(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestParam(name = "sort", required = false) String sortParams) {
        var user = userRepository.findFromToken(authorization);
        if(user == null)
            throw MoneyGestorErrorSample.USER_NOT_FOUND;

        UserGestor userGestor = UserGestor.Builder.createFromDB(user);

        if(!userGestor.tokenIsValid())
            throw MoneyGestorErrorSample.USER_TOKEN_NOT_VALID;

        TransactionTableView transactionExample = new TransactionTableView();
        transactionExample.setUser(userGestor.getId());

        Sort sort = SortGestor.decode(sortParams);

        return transactionTableRepository.findAll(Example.of(transactionExample), sort);
    }

    @GetMapping("/get/{id}")
    public Transaction getAllTransaction(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Integer id) {
        var user = userRepository.findFromToken(authorization);
        if(user == null)
            throw MoneyGestorErrorSample.USER_NOT_FOUND;

        UserGestor userGestor = UserGestor.Builder.createFromDB(user);

        if(!userGestor.tokenIsValid())
            throw MoneyGestorErrorSample.USER_TOKEN_NOT_VALID;

        TransactionDb transactionExample = new TransactionDb();
        transactionExample.setUserId(userGestor.getId());
        transactionExample.setId(id);

        try {
            return TransactionGestor.convertToRest(transactionRepository.findOne(Example.of(transactionExample)).get());
        } catch (NoSuchElementException ignored) {
            throw MoneyGestorErrorSample.USER_NOT_HAVE_PERMISSION;
        }
    }

    @PostMapping("/edit/{id}")
    public void editTransaction(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody TransactionForm transaction, @PathVariable Integer id) {
        if(authorization == null)
            throw MoneyGestorErrorSample.LOGIN_REQUIRED;

        try {
            UserGestor userGestor = UserGestor.Builder.createFromDB(userRepository.findFromToken(authorization));
            if(!userGestor.tokenIsValid())
                throw MoneyGestorErrorSample.USER_TOKEN_NOT_VALID;

            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

            TransactionDb transactionSource = transactionRepository.findById(id).get();

            transactionSource.setDescription(transaction.getDescription());
            transactionSource.setDate(LocalDate.parse(transaction.getDate(), formatter));
            transactionSource.setValue(transaction.getTypeId() == ID_EXCHANGE_TYPE? transaction.getValue().negate() : transaction.getValue());
            transactionSource.setTypeId(transaction.getTypeId());
            transactionSource.setWalletId(transaction.getWallet());

            transactionRepository.save(transactionSource);

            if(transaction.getTypeId() != ID_EXCHANGE_TYPE)
                return;

            TransactionDb transactionDestination = transactionRepository.findById(transactionSource.getTransactionDestinationId()).get();

            transactionDestination.setDescription(transaction.getDescription());
            transactionDestination.setDate(LocalDate.parse(transaction.getDate(), formatter));
            transactionDestination.setValue(transaction.getValue());
            transactionDestination.setTypeId(transaction.getTypeId());
            transactionDestination.setWalletId(transaction.getWalletDestination());

            transactionRepository.save(transactionDestination);

            transactionRepository.flush();

        } catch (IllegalArgumentException e) {
            throw MoneyGestorErrorSample.USER_NOT_FOUND;
        } catch (NoSuchElementException ignored) {
            throw MoneyGestorErrorSample.GENERIC_ERROR;
        }
    }

    @GetMapping("/delete/{id}")
    public void deleteTransaction(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Integer id) {
        var user = userRepository.findFromToken(authorization);
        if(user == null)
            throw MoneyGestorErrorSample.USER_NOT_FOUND;

        UserGestor userGestor = UserGestor.Builder.createFromDB(user);

        if(!userGestor.tokenIsValid())
            throw MoneyGestorErrorSample.USER_TOKEN_NOT_VALID;

        TransactionDb transactionExample = new TransactionDb();
        transactionExample.setUserId(userGestor.getId());
        transactionExample.setId(id);

        transactionRepository.delete(transactionExample);
    }

    @GetMapping("/graph")
    public List<LineGraph<LocalDate, BigDecimal>> getGraph(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        var user = userRepository.findFromToken(authorization);
        if(user == null)
            throw MoneyGestorErrorSample.USER_NOT_FOUND;

        UserGestor userGestor = UserGestor.Builder.createFromDB(user);

        if(!userGestor.tokenIsValid())
            throw MoneyGestorErrorSample.USER_TOKEN_NOT_VALID;

        List<LineGraph<LocalDate, BigDecimal>> graph = new LinkedList<>();

        List<WalletDb> walletDbs = walletRepository.getWalletsFromUser(userGestor.getId());

        for(var wallet : walletDbs) {
            Query query = entityManager.createNativeQuery("SELECT Value - (SELECT SUM(Value) FROM transaction WHERE Wallet = :id AND Date BETWEEN :dateStart AND :dateEnd) AS Value FROM wallet WHERE Id = :id");
            query.setParameter("id", wallet.getId()).setParameter("dateStart", LocalDate.of(1970,Month.JANUARY,1)).setParameter("dateEnd", LocalDate.now());
            BigDecimal walletValue = (BigDecimal) query.getResultList().get(0);

            LineGraph<LocalDate, BigDecimal> lineGraph = new LineGraph<>();
            lineGraph.setId(wallet.getName());
            lineGraph.setColor(wallet.getColor());
            lineGraph.setData(new LinkedList<>());

            TransactionGraphView transactionExample = new TransactionGraphView();
            transactionExample.setWallet(wallet.getId());

            List<TransactionGraphView> transactions = transactionGraphRepository.findAll(Example.of(transactionExample));

            for(var transaction : transactions) {
                walletValue = walletValue.add(transaction.getValue());

                var graphElement = new LineGraph.DataElement<LocalDate, BigDecimal>();
                graphElement.setX(transaction.getDate());
                graphElement.setY(walletValue);

                lineGraph.getData().add(graphElement);
            }

            graph.add(lineGraph);
        }

        return graph;
    }
}
