package com.alkemy.wallet.service;

import com.alkemy.wallet.dto.*;
import com.alkemy.wallet.exception.*;
import com.alkemy.wallet.model.Account;
import com.alkemy.wallet.model.FixedTermDeposit;
import com.alkemy.wallet.model.User;
import com.alkemy.wallet.model.enums.Currency;
import com.alkemy.wallet.repository.IAccountRepository;
import com.alkemy.wallet.repository.IFixedTermRepository;
import com.alkemy.wallet.repository.IUserRepository;
import com.alkemy.wallet.service.interfaces.IAccountService;
import com.alkemy.wallet.service.interfaces.IUserService;
import com.alkemy.wallet.util.JwtUtil;
import io.swagger.v3.oas.annotations.Hidden;
import org.modelmapper.ModelMapper;
import org.springframework.context.MessageSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Hidden
@Service
public class AccountService implements IAccountService {

    private final IAccountRepository accountRepository;
    private final IFixedTermRepository fixedTermRepository;
    private final IUserService userService;
    private final IUserRepository userRepository;
    private final ModelMapper mapper;
    private final JwtUtil jwtUtil;
    private final MessageSource messageSource;

    public AccountService(IAccountRepository accountRepository, IFixedTermRepository fixedTermRepository, IUserService userService, IUserRepository userRepository, ModelMapper mapper, JwtUtil jwtUtil, MessageSource messageSource) {
        this.accountRepository = accountRepository;
        this.fixedTermRepository = fixedTermRepository;
        this.userService = userService;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.jwtUtil = jwtUtil;
        this.messageSource = messageSource;
    }

    @Override
    public BasicAccountDto createAccount(AccountCreateDto accountCreateDto, User user) {

        if (user != null) {
            List<Account> accounts = accountRepository.findAllByUser_Email(user.getEmail());

            if (accounts.stream()
                    .anyMatch(c -> c.getCurrency().equals(accountCreateDto.getCurrency()))) {
                throw new AccountAlreadyExistsException(messageSource.getMessage(
                        "account.found.foruser.exception",
                        new Object[] {accountCreateDto.getCurrency()},
                        Locale.ENGLISH));
            }
        }

        Account account = new Account(accountCreateDto.getCurrency());

        account.setUser(user);
        account.setCreationDate(new Date());

        System.out.println(account);

        return mapper.map(accountRepository.save(account), BasicAccountDto.class);

    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> getAccountsByUserId(Long userId) throws EmptyResultDataAccessException {
        List<Account> accounts = accountRepository.findAllByUser_Id(userId);

        if (accounts.isEmpty()) {
            throw new EmptyResultDataAccessException("User has no accounts", 1);
        }
        return accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountDto> findAllAccountsPageable(int page) throws EmptyResultDataAccessException {

        Pageable pageable = PageRequest.of(page, 10);

        return accountRepository.findAll(pageable).map(account ->
                mapper.map(account, AccountDto.class));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDto> getAccountsByUserEmail(String email) throws EmptyResultDataAccessException {
        List<Account> accounts = accountRepository.findAllByUser_Email(email);

        if (accounts.isEmpty()) {
            throw new EmptyResultDataAccessException("User has no accounts", 1);
        }
        return accounts.stream().map(account ->
                mapper.map(account, AccountDto.class)
        ).toList();
    }

    @Override
    public Account getAccountByCurrency(Long userId, Currency currency) {

        List<Account> userAccounts = getAccountsByUserId(userId);
        for (Account account : userAccounts) {
            if (account.getCurrency() == currency) {
                return account;
            }
        }
        return null;
    }

    @Override
    public boolean checkAccountLimit(Account senderAccount, RequestTransactionDto transactionDto) {
        if (transactionDto.getAmount() < senderAccount.getTransactionLimit())
            return true;
        else throw new AccountLimitException("Account transaction limit exceeded");
    }

    public ResponseEntity<?> updateAccount(Long id, AccountUpdateDto newTransactionLimit) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = userRepository.findByEmail(auth.getName());
            Account account = accountRepository.findById(id).orElseThrow(()
                    -> new ResourceNotFoundException(messageSource.getMessage("account.notfound.exception",
                    new Object[] {id}, Locale.ENGLISH)));
            List<Account> accounts = accountRepository.findAllByUser_Email(user.getEmail());

            if (accounts.stream().noneMatch(c -> c.getId().equals(id))) {
                throw new ResourceNotFoundException(messageSource.getMessage("account.notfound.foruser.exception",
                        new Object[] {id}, Locale.ENGLISH));
            }

            mapper.map(newTransactionLimit, account);
            Account accountUpdated = accountRepository.save(account);
            BasicAccountDto basicAccountDto = mapper.map(accountUpdated, BasicAccountDto.class);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapper.map(accountUpdated, BasicAccountDto.class));
        } catch (UserNotLoggedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e);
        }
    }

    @Override
    public boolean checkAccountExistence(Long user_id, Currency currency) {
        List<Account> accounts = accountRepository.findAllByUser_Id(user_id);
        for (Account account : accounts) {
            if (account.getCurrency() == currency) {
                throw new AccountAlreadyExistsException("Account already exists");
            }
        }
        return false;
    }

    @Override
    public ResponseEntity<?> postAccount(BasicAccountDto basicAccountDto) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = userRepository.findByEmail(auth.getName());
            return ResponseEntity.status(HttpStatus.OK).body(createAccount(mapper.map(basicAccountDto, AccountCreateDto.class), user));

        } catch (UserNotLoggedException | AccountAlreadyExistsException e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e);
        }
    }

    @Override
    public List<BalanceDto> getBalance() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName());
        List<AccountDto> accounts = getAccountsByUserEmail(user.getEmail());
        return accounts
                .stream()
                .map(this::getBalanceByAccount)
                .collect(Collectors.toList());
    }

    private BalanceDto getBalanceByAccount(AccountDto accountDto) {
        BalanceDto balanceDto = mapper.map(accountDto, BalanceDto.class);
        List<FixedTermDeposit> fixedTermList = fixedTermRepository.findAllByAccount_Id(accountDto.getId());
        List<FixedTermDto> fixedTermDtoList = fixedTermList
                .stream()
                .map(fixedTerm -> mapper.map(fixedTerm, FixedTermDto.class))
                .collect(Collectors.toList());
        fixedTermDtoList.stream().forEach(fixedTermDto -> fixedTermDto.setCurrency(accountDto.getCurrency()));
        balanceDto.setFixedTerm(fixedTermDtoList);
        return balanceDto;
    }

    @Override
    public AccountDto updateBalance(Long id, Double amount) {
        if (amount <= 0) {
            throw new NoAmountException(messageSource.getMessage("amount.exception", null, Locale.ENGLISH));
        }
        Optional<Account> foundAccount = accountRepository.findById(id);
        if (!foundAccount.isPresent()) {
            throw new ResourceFoundException(messageSource.getMessage("account.notfound.exception", new Object[] {id}, Locale.ENGLISH));
        }
        if (foundAccount.get().getBalance() < amount) {
            throw new NotEnoughCashException(messageSource.getMessage("notenoughcash.exception", null, Locale.ENGLISH));
        }
        Account account = foundAccount.get();
        account.setBalance(account.getBalance() - amount);

        return mapper.map(account, AccountDto.class);
    }


}