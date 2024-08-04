package com.product.globie.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "Wallet")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int walletId;

    @Column(nullable = false)
    private double balance;

    @Column
    private LocalDate createAt;

    @Column
    private LocalDate updateAt;

    @Column(name = "status")
    private boolean status;

    @OneToOne
    @JoinColumn(name = "accountId")
    private Account account;
}